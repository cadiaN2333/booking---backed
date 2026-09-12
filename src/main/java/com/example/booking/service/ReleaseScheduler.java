package com.example.booking.service;

import com.example.booking.domain.entity.Reservation;
import com.example.booking.mapper.ReservationMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * 超时释放调度器。
 *
 * <p>两条路径，主 + 兜底：
 * <ol>
 *   <li>主：下单时投递一个延迟任务，到期触发 release（真实项目里换成 RocketMQ 定时消息 / RabbitMQ 延迟插件）。</li>
 *   <li>兜底：每分钟扫描 status=0 且已过期的订单（对应 XXL-Job 补偿 Job），
 *       覆盖进程重启、延迟消息丢失等场景。</li>
 * </ol>
 *
 * <p>两条路径最终都调用幂等的 {@code release()}，靠状态机 CAS 保证不会重复归还库存。
 */
@Slf4j
@Component
public class ReleaseScheduler {

  private static final int SWEEP_LIMIT = 200;

  private final ReservationService reservationService;
  private final ReservationMapper reservationMapper;
  private final DelayQueue<ReleaseTask> queue = new DelayQueue<>();

  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "slot-release-worker");
            t.setDaemon(true);
            return t;
          });

  // ReservationServiceImpl 依赖本类做延迟投递，@Lazy 打破构造循环
  public ReleaseScheduler(
      @Lazy ReservationService reservationService, ReservationMapper reservationMapper) {
    this.reservationService = reservationService;
    this.reservationMapper = reservationMapper;
  }

  @PostConstruct
  public void start() {
    worker.submit(
        () -> {
          while (!Thread.currentThread().isInterrupted()) {
            try {
              ReleaseTask task = queue.take();
              reservationService.release(task.orderNo());
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              break;
            } catch (Exception e) {
              log.error("延迟释放任务执行失败", e);
            }
          }
        });
  }

  @PreDestroy
  public void stop() {
    worker.shutdownNow();
  }

  public void schedule(String orderNo, LocalDateTime expireAt) {
    long triggerAt = expireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    queue.put(new ReleaseTask(orderNo, triggerAt));
  }

  /** 补偿 Job：兜住进程重启与消息丢失 */
  @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
  public void sweep() {
    List<Reservation> expired = reservationMapper.selectExpired(SWEEP_LIMIT);
    for (Reservation r : expired) {
      try {
        reservationService.release(r.getOrderNo());
      } catch (Exception e) {
        log.error("补偿释放失败, orderNo={}", r.getOrderNo(), e);
      }
    }
  }

  record ReleaseTask(String orderNo, long triggerAt) implements Delayed {

    @Override
    public long getDelay(TimeUnit unit) {
      return unit.convert(triggerAt - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
      return Long.compare(this.triggerAt, ((ReleaseTask) other).triggerAt);
    }
  }
}
