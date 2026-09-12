package com.example.booking.service;

import com.example.booking.domain.entity.ReleaseTask;
import com.example.booking.domain.entity.Reservation;
import com.example.booking.mapper.ReleaseTaskMapper;
import com.example.booking.mapper.ReservationMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 预约超时释放调度器：持久化任务为主，订单扫表为兜底。 */
@Slf4j
@Component
public class ReleaseScheduler {

  private static final int TASK_LIMIT = 100;
  private static final int SWEEP_LIMIT = 200;
  private static final long[] RETRY_DELAYS_SECONDS = {5, 15, 30, 60};

  private final ReservationService reservationService;
  private final ReservationMapper reservationMapper;
  private final ReleaseTaskMapper releaseTaskMapper;

  public ReleaseScheduler(
      @Lazy ReservationService reservationService,
      ReservationMapper reservationMapper,
      ReleaseTaskMapper releaseTaskMapper) {
    this.reservationService = reservationService;
    this.reservationMapper = reservationMapper;
    this.releaseTaskMapper = releaseTaskMapper;
  }

  /** 与创建预约处于同一个事务，事务回滚时任务也会回滚。 */
  public void schedule(String orderNo, LocalDateTime expireAt) {
    ReleaseTask task = new ReleaseTask();
    task.setOrderNo(orderNo);
    task.setExecuteAt(expireAt);
    task.setStatus(0);
    task.setAttempts(0);
    releaseTaskMapper.insert(task);
  }

  /** 订单进入确认或取消等终态时，不再等待到期时间处理释放任务。 */
  public void finish(String orderNo) {
    releaseTaskMapper.finishByOrderNo(orderNo);
  }

  /** 每轮先恢复崩溃实例遗留的处理中任务，再抢占到期任务。 */
  @Scheduled(fixedDelay = 5_000, initialDelay = 10_000)
  public void processDueTasks() {
    try {
      releaseTaskMapper.recoverStuck();
      List<ReleaseTask> tasks = releaseTaskMapper.selectDue(TASK_LIMIT);
      for (ReleaseTask task : tasks) {
        processOne(task);
      }
    } catch (RuntimeException e) {
      log.error("释放任务轮询失败", e);
    }
  }

  void processOne(ReleaseTask task) {
    if (releaseTaskMapper.claim(task.getId()) != 1) {
      return;
    }

    try {
      // release 使用订单状态 CAS，重复任务只会有一次真正的库存释放。
      reservationService.release(task.getOrderNo());
      releaseTaskMapper.markDone(task.getId());
    } catch (RuntimeException e) {
      int attempt = task.getAttempts() == null ? 1 : task.getAttempts() + 1;
      int delayIndex = Math.min(attempt - 1, RETRY_DELAYS_SECONDS.length - 1);
      LocalDateTime nextExecuteAt =
          LocalDateTime.now().plusSeconds(RETRY_DELAYS_SECONDS[delayIndex]);
      releaseTaskMapper.retry(task.getId(), nextExecuteAt, shortenError(e));
      log.error("预约释放失败，已安排重试 orderNo={} attempt={}", task.getOrderNo(), attempt, e);
    }
  }

  /** 兜底扫描覆盖旧数据、手工数据以及任务表写入前的历史订单。 */
  @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
  public void sweep() {
    try {
      List<Reservation> expired = reservationMapper.selectExpired(SWEEP_LIMIT);
      for (Reservation reservation : expired) {
        try {
          reservationService.release(reservation.getOrderNo());
        } catch (RuntimeException e) {
          log.error("补偿释放失败 orderNo={}", reservation.getOrderNo(), e);
        }
      }
    } catch (RuntimeException e) {
      log.error("补偿释放扫描失败", e);
    }
  }

  private String shortenError(RuntimeException e) {
    String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    return message.length() <= 500 ? message : message.substring(0, 500);
  }
}
