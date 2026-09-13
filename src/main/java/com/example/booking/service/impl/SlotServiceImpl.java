package com.example.booking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.booking.common.BizException;
import com.example.booking.common.UserContext;
import com.example.booking.domain.entity.Court;
import com.example.booking.domain.entity.Slot;
import com.example.booking.domain.entity.Venue;
import com.example.booking.domain.vo.SlotVO;
import com.example.booking.mapper.CourtMapper;
import com.example.booking.mapper.ReservationMapper;
import com.example.booking.mapper.SlotMapper;
import com.example.booking.mapper.VenueMapper;
import com.example.booking.service.SlotCacheService;
import com.example.booking.service.SlotService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlotServiceImpl implements SlotService {

  private final SlotMapper slotMapper;
  private final CourtMapper courtMapper;
  private final VenueMapper venueMapper;
  private final ReservationMapper reservationMapper;
  private final SlotCacheService slotCacheService;

  @Value("${booking.generate-days:14}")
  private int generateDays;

  /** 按场地类型决定单时段总库存：场地类一次只放 1 个，自习室按座位数放 */
  private static final Map<String, Integer> TOTAL_BY_TYPE = Map.of("自习室", 20);

  @Override
  @Transactional(rollbackFor = Exception.class)
  public int generateForMerchant(Long courtId) {
    Court court = courtMapper.selectById(courtId);
    if (court == null) {
      throw new BizException("场地不存在");
    }

    // 归属校验，防水平越权：A 商家不能操作 B 商家的场地
    Venue venue = venueMapper.selectById(court.getVenueId());
    if (venue == null || !UserContext.userId().equals(venue.getMerchantId())) {
      throw new BizException(4030, "无权操作该场地");
    }
    if (!isPublished(court) || !isPublished(venue)) {
      throw new BizException(4030, "场地未审核通过或未上架");
    }

    return generate(courtId, generateDays);
  }

  @Override
  public List<SlotVO> listByCourtAndDate(Long courtId, LocalDate date) {
    Court court = courtMapper.selectById(courtId);
    if (!isPublished(court)) {
      return List.of();
    }
    Venue venue = venueMapper.selectById(court.getVenueId());
    if (!isPublished(venue)) {
      return List.of();
    }

    try {
      return listByCourtAndDateWithCache(courtId, date);
    } catch (RuntimeException e) {
      // 缓存是性能优化而非可用性前提；Redis 故障时公开查询必须仍可用。
      log.warn(
          "时段缓存不可用，回源数据库 courtId={} date={} reason={}",
          courtId,
          date,
          e.getMessage());
      return loadFromDb(courtId, date.toString());
    }
  }

  private List<SlotVO> listByCourtAndDateWithCache(Long courtId, LocalDate date) {
    String dateStr = date.toString();

    String raw = slotCacheService.getRawDay(courtId, dateStr);

    // ① 空值缓存命中 —— 防穿透。直接返回空，绝不打 DB
    if (SlotCacheService.NULL_MARK.equals(raw)) {
      return List.of();
    }

    // ② 缓存命中
    if (raw != null) {
      SlotCacheService.CachedDay cached = slotCacheService.parse(raw);
      if (cached != null) {
        if (System.currentTimeMillis() <= cached.expireTs()) {
          return cached.slots();
        }
        // 逻辑已过期：抢锁重建
        if (slotCacheService.tryLockRebuild(courtId, dateStr)) {
          try {
            List<SlotVO> fresh = loadFromDb(courtId, dateStr);
            slotCacheService.putDay(courtId, dateStr, fresh);
            return fresh;
          } finally {
            slotCacheService.unlockRebuild(courtId, dateStr);
          }
        }
        // 没抢到锁：先用旧值兜着，不阻塞用户（牺牲一致性换可用性）
        return cached.slots();
      }
    }

    // ③ 未命中：回源 DB
    List<SlotVO> slots = loadFromDb(courtId, dateStr);
    if (slots.isEmpty()) {
      slotCacheService.putNull(courtId, dateStr);
    } else {
      slotCacheService.putDay(courtId, dateStr, slots);
      // 顺手预热库存计数，供后面的 Lua 预扣用
      for (SlotVO s : slots) {
        slotCacheService.warmUpStock(s.getId(), s.getAvailable());
      }
    }
    return slots;
  }

  /** 原来的查库逻辑抽出来 */
  private List<SlotVO> loadFromDb(Long courtId, String dateStr) {
    Court court = courtMapper.selectById(courtId);
    int price = court == null ? 0 : court.getPrice();
    return slotMapper
            .selectList(new LambdaQueryWrapper<Slot>()
                    .eq(Slot::getCourtId, courtId)
                    .eq(Slot::getBizDate, LocalDate.parse(dateStr))
                    .orderByAsc(Slot::getStartAt))
            .stream()
            .map(s -> toVO(s, price))
            .toList();
  }

  private boolean isPublished(Court court) {
    return court != null && Integer.valueOf(1).equals(court.getAuditStatus())
        && Integer.valueOf(1).equals(court.getStatus());
  }

  private boolean isPublished(Venue venue) {
    return venue != null && Integer.valueOf(1).equals(venue.getAuditStatus())
        && Integer.valueOf(1).equals(venue.getStatus());
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public int generate(Long courtId, int days) {
    Court court = courtMapper.selectById(courtId);
    if (court == null) {
      throw new BizException("场地不存在");
    }

    LocalDate today = LocalDate.now();
    LocalDate lastDay = today.plusDays(days - 1L);

    List<Slot> wanted = buildWindows(court, today, days);
    Set<String> wantedKeys = wanted.stream().map(SlotServiceImpl::windowKey).collect(Collectors.toSet());

    List<Slot> existing =
        slotMapper.selectList(
            new LambdaQueryWrapper<Slot>()
                .eq(Slot::getCourtId, courtId)
                .ge(Slot::getBizDate, today)
                .le(Slot::getBizDate, lastDay));
    Set<String> existingKeys =
        existing.stream().map(SlotServiceImpl::windowKey).collect(Collectors.toSet());

    // 被订单引用过的时段一行都不能删：删了订单 slot_id 会悬空，
    // 更严重的是已售计数归零，这个时间段会被重复卖出。
    Set<Long> referenced = new HashSet<>(reservationMapper.selectReferencedSlotIds(courtId));

    // 1) 清理不想要的窗口（改了营业时间时才会用到），但绝不碰有订单的行
    for (Slot s : existing) {
      if (!wantedKeys.contains(windowKey(s)) && !referenced.contains(s.getId())) {
        slotMapper.deleteById(s.getId());
      }
    }

    // 2) 只补缺失的。已存在的行原样保留，sold / locked 计数不会被清零
    int created = 0;
    for (Slot w : wanted) {
      if (existingKeys.contains(windowKey(w))) {
        continue;
      }
      slotMapper.insert(w);
      created++;
    }

    log.info("场地 {} 补齐时段 {} 个（目标 {} 天，已存在 {} 个已跳过）", courtId, created, days, existing.size());
    return created;
  }

  /** 按营业时间切出未来 days 天的全部时段窗口（未入库，id 为空） */
  private List<Slot> buildWindows(Court court, LocalDate from, int days) {
    int total = TOTAL_BY_TYPE.getOrDefault(court.getType(), 1);
    List<Slot> list = new ArrayList<>();

    for (int d = 0; d < days; d++) {
      LocalDate date = from.plusDays(d);
      LocalDateTime cursor = LocalDateTime.of(date, court.getOpenTime());
      LocalDateTime close = LocalDateTime.of(date, court.getCloseTime());

      while (cursor.plusMinutes(court.getSlotMinutes()).compareTo(close) <= 0) {
        LocalDateTime end = cursor.plusMinutes(court.getSlotMinutes());

        Slot slot = new Slot();
        slot.setCourtId(court.getId());
        slot.setBizDate(date);
        slot.setStartAt(cursor);
        slot.setEndAt(end);
        slot.setTotal(total);
        slot.setAvailable(total);
        slot.setLocked(0);
        slot.setSold(0);
        slot.setVersion(0);
        list.add(slot);

        cursor = end;
      }
    }
    return list;
  }

  /** 时段唯一标识：业务日期 + 开始时间 */
  private static String windowKey(Slot s) {
    return s.getBizDate() + " " + s.getStartAt();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public int generateAll(int days) {
    int sum = 0;
    for (Court court : courtMapper.selectList(
        new LambdaQueryWrapper<Court>()
            .eq(Court::getAuditStatus, 1)
            .eq(Court::getStatus, 1))) {
      sum += generate(court.getId(), days);
    }
    return sum;
  }

  private SlotVO toVO(Slot s, int price) {
    SlotVO v = new SlotVO();
    BeanUtils.copyProperties(s, v);
    v.setPrice(price);
    return v;
  }
}
