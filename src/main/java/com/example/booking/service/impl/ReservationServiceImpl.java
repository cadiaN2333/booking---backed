package com.example.booking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.booking.common.BizException;
import com.example.booking.common.UserContext;
import com.example.booking.domain.dto.CreateReservationRequest;
import com.example.booking.domain.entity.Court;
import com.example.booking.domain.entity.IdempotentRecord;
import com.example.booking.domain.entity.Reservation;
import com.example.booking.domain.entity.Slot;
import com.example.booking.domain.enums.ReservationStatusEnum;
import com.example.booking.domain.vo.ReservationVO;
import com.example.booking.mapper.CourtMapper;
import com.example.booking.mapper.IdempotentMapper;
import com.example.booking.mapper.ReservationMapper;
import com.example.booking.mapper.SlotMapper;
import com.example.booking.service.ReleaseScheduler;
import com.example.booking.service.ReservationService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.example.booking.service.SlotCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

  private static final String BIZ_TYPE = "RESERVATION";
  private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  private final ReservationMapper reservationMapper;
  private final SlotMapper slotMapper;
  private final CourtMapper courtMapper;
  private final IdempotentMapper idempotentMapper;
  private final ReleaseScheduler releaseScheduler;
  private final SlotCacheService slotCacheService;


  /** 下单后锁定时长（分钟），超时未确认则释放 */
  @Value("${booking.lock-minutes:15}")
  private int lockMinutes;

  @Override
  public String createToken(Long slotId) {
    String token = UUID.randomUUID().toString().replace("-", "");
    IdempotentRecord record = new IdempotentRecord();
    record.setToken(token);
    record.setUserId(UserContext.userId());
    record.setBizType(BIZ_TYPE);
    record.setResult(null);
    idempotentMapper.insert(record);
    return token;
  }

  /**
   * 下单主链路。
   *
   * <p>顺序很关键：先占令牌（幂等） -> 再扣库存（原子） -> 最后建单。
   * 任一步失败都由 @Transactional 整体回滚，令牌的 result 也会随之回滚为 NULL，
   * 因此用户重试时仍能正常消费。
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public ReservationVO create(CreateReservationRequest request) {
    // userId 一律取登录态，前端传什么都不认，杜绝「替别人下单」
    Long userId = UserContext.userId();

    // 1) 幂等：抢占令牌，失败说明是重复提交
    int consumed = idempotentMapper.consume(request.getToken(), userId, BIZ_TYPE);
    if (consumed == 0) {
      throw new BizException("请勿重复提交");
    }

    // 2) 重复预约校验。uk_user_slot 唯一索引是最后一道防线，这里只是提前给友好提示
    if (reservationMapper.countActive(userId, request.getSlotId()) > 0) {
      throw new BizException("你已预约过该时段");
    }

    // 3) Redis 仅用于削峰；不可用时降级到数据库最终裁决，不能阻断下单。
    boolean cacheDeducted = tryDeductCache(request.getSlotId());

    // 4) 数据库原子扣减是唯一最终裁决，整条下单链路只能执行一次。
    if (slotMapper.deductAvailable(request.getSlotId()) == 0) {
      // DB 说没有了（Redis 计数漂了）→ 仅回补本次成功的预扣。
      if (cacheDeducted) {
        safeGiveBack(request.getSlotId());
      }
      throw new BizException("该时段已被抢走");
    }

    Slot slot = slotMapper.selectById(request.getSlotId());
    if (slot == null) {
      throw new BizException("时段不存在");
    }
    // 即使 Redis 曾经漂移，也以本次数据库扣减后的最新值覆盖它。
    refreshStockCache(slot.getId(), slot);

    LocalDateTime now = LocalDateTime.now();
    Reservation r = new Reservation();
    r.setOrderNo(genOrderNo(now));
    r.setUserId(userId);
    r.setCourtId(slot.getCourtId());
    r.setSlotId(slot.getId());
    r.setBizDate(slot.getBizDate());
    r.setStartAt(slot.getStartAt());
    r.setEndAt(slot.getEndAt());
    r.setAmount(priceOf(slot.getCourtId()));
    r.setStatus(ReservationStatusEnum.PENDING.getCode());
    r.setExpireAt(now.plusMinutes(lockMinutes));
    r.setVersion(0);
    reservationMapper.insert(r);

    idempotentMapper.finish(request.getToken(), r.getOrderNo());

    // 4) 预约超时释放。主链路走延迟队列，另有每分钟的补偿 Job 兜底
    releaseScheduler.schedule(r.getOrderNo(), r.getExpireAt());

    safeEvictDay(slot.getCourtId(), slot.getBizDate().toString());
    log.info("下单成功 orderNo={} slotId={} userId={}", r.getOrderNo(), slot.getId(), r.getUserId());
    return toVO(r);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void confirm(String orderNo) {
    Reservation r = requireOwned(orderNo);

    // 状态机 CAS：只有当前是「待支付」才能流转到「已确认」。
    // 与超时释放竞争同一行行锁，二者只有一个能成功，因此不需要分布式锁。
    int n =
        reservationMapper.updateStatus(
            orderNo,
            ReservationStatusEnum.PENDING.getCode(),
            ReservationStatusEnum.CONFIRMED.getCode());
    if (n == 0) {
      throw friendlyStatusError(orderNo);
    }

    slotMapper.confirmLocked(r.getSlotId());
    refreshStockCache(r.getSlotId());
    safeEvictDay(r.getCourtId(), r.getBizDate().toString());
    releaseScheduler.finish(orderNo);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void cancel(String orderNo) {
    Reservation r = requireOwned(orderNo);

    int n =
        reservationMapper.updateStatus(
            orderNo,
            ReservationStatusEnum.PENDING.getCode(),
            ReservationStatusEnum.CANCELLED.getCode());
    if (n == 0) {
      throw friendlyStatusError(orderNo);
    }

    slotMapper.releaseLocked(r.getSlotId());
    refreshStockCache(r.getSlotId());
    safeEvictDay(r.getCourtId(), r.getBizDate().toString());
    releaseScheduler.finish(orderNo);
  }

  /** 归属校验，防止通过猜订单号操作别人的预约 */
  private Reservation requireOwned(String orderNo) {
    Reservation r = reservationMapper.selectByOrderNo(orderNo);
    if (r == null) {
      throw new BizException("订单不存在");
    }
    if (!UserContext.userId().equals(r.getUserId())) {
      throw new BizException(4030, "无权操作该订单");
    }
    return r;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean release(String orderNo) {
    // 影响行数 0 = 已被确认 / 取消 / 已释放过，幂等跳过，绝不重复归还库存
    int n =
        reservationMapper.updateStatus(
            orderNo,
            ReservationStatusEnum.PENDING.getCode(),
            ReservationStatusEnum.EXPIRED.getCode());
    if (n == 0) {
      return false;
    }

    Reservation r = reservationMapper.selectByOrderNo(orderNo);
    slotMapper.releaseLocked(r.getSlotId());
    refreshStockCache(r.getSlotId());
    safeEvictDay(r.getCourtId(), r.getBizDate().toString());
    log.info("超时释放成功 orderNo={}", orderNo);
    return true;
  }

  @Override
  public List<ReservationVO> listMine() {
    return reservationMapper
        .selectList(
            new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, UserContext.userId())
                .orderByDesc(Reservation::getCreateTime))
        .stream()
        .map(this::toVO)
        .toList();
  }

  @Override
  public ReservationVO getByOrderNo(String orderNo) {
    return toVO(requireOwned(orderNo));
  }

  private BizException friendlyStatusError(String orderNo) {
    Reservation r = reservationMapper.selectByOrderNo(orderNo);
    if (r == null) {
      return new BizException("订单不存在");
    }
    if (r.getStatus() == ReservationStatusEnum.EXPIRED.getCode()) {
      return new BizException("订单已超时释放，请重新下单");
    }
    return new BizException("订单状态已变更，请刷新");
  }

  private int priceOf(Long courtId) {
    Court court = courtMapper.selectById(courtId);
    return court == null ? 0 : court.getPrice();
  }

  /** Redis 预扣失败时回退数据库，不让缓存依赖影响交易正确性。 */
  private boolean tryDeductCache(Long slotId) {
    try {
      String deducted = slotCacheService.tryDeduct(slotId);
      // Redis 可能遗留旧计数；EMPTY 只能作为削峰提示，库存最终由 MySQL 条件更新裁决。
      if ("EMPTY".equals(deducted)) {
        return false;
      }
      if (!"UNREADY".equals(deducted)) {
        return "OK".equals(deducted);
      }

      Slot warm = slotMapper.selectById(slotId);
      if (warm != null) {
        slotCacheService.alignStock(warm.getId(), warm.getAvailable());
      }
      String retried = slotCacheService.tryDeduct(slotId);
      if ("EMPTY".equals(retried)) {
        throw new BizException("该时段已被抢走");
      }
      return "OK".equals(retried);
    } catch (BizException e) {
      throw e;
    } catch (RuntimeException e) {
      log.warn("Redis 库存预扣不可用，降级到数据库 slotId={}", slotId, e);
      return false;
    }
  }

  /** 缓存写失败不应导致已完成的数据库交易回滚。 */
  private void refreshStockCache(Long slotId) {
    Slot latest = slotMapper.selectById(slotId);
    refreshStockCache(slotId, latest);
  }

  private void refreshStockCache(Long slotId, Slot latest) {
    if (latest == null) {
      return;
    }
    try {
      slotCacheService.alignStock(latest.getId(), latest.getAvailable());
    } catch (RuntimeException e) {
      log.warn("同步 Redis 库存失败 slotId={}", slotId, e);
    }
  }

  private void safeGiveBack(Long slotId) {
    try {
      slotCacheService.giveBack(slotId);
    } catch (RuntimeException e) {
      log.warn("回补 Redis 库存失败 slotId={}", slotId, e);
    }
  }

  private void safeEvictDay(Long courtId, String date) {
    try {
      slotCacheService.evictDay(courtId, date);
    } catch (RuntimeException e) {
      log.warn("删除时段缓存失败 courtId={} date={}", courtId, date, e);
    }
  }

  private String genOrderNo(LocalDateTime now) {
    int tail = ThreadLocalRandom.current().nextInt(1000, 9999);
    return "B" + now.format(ORDER_NO_FMT) + tail;
  }

  private ReservationVO toVO(Reservation r) {
    ReservationVO v = new ReservationVO();
    BeanUtils.copyProperties(r, v);
    Court court = courtMapper.selectById(r.getCourtId());
    v.setCourtName(court == null ? "" : court.getName());
    return v;
  }
}
