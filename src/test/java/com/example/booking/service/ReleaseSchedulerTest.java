package com.example.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.booking.domain.entity.ReleaseTask;
import com.example.booking.mapper.ReleaseTaskMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReleaseSchedulerTest {

  private ReservationService reservationService;
  private ReleaseTaskMapper releaseTaskMapper;
  private ReleaseScheduler scheduler;

  @BeforeEach
  void setUp() {
    reservationService = mock(ReservationService.class);
    releaseTaskMapper = mock(ReleaseTaskMapper.class);
    scheduler = new ReleaseScheduler(reservationService, mock(), releaseTaskMapper);
  }

  @Test
  void schedule_将释放任务持久化() {
    LocalDateTime expireAt = LocalDateTime.of(2026, 9, 12, 17, 0);

    scheduler.schedule("B1", expireAt);

    var taskCaptor = org.mockito.ArgumentCaptor.forClass(ReleaseTask.class);
    verify(releaseTaskMapper).insert(taskCaptor.capture());
    ReleaseTask task = taskCaptor.getValue();
    assertThat(task.getOrderNo()).isEqualTo("B1");
    assertThat(task.getExecuteAt()).isEqualTo(expireAt);
    assertThat(task.getStatus()).isZero();
  }

  @Test
  void processDueTasks_成功后标记完成() {
    ReleaseTask task = task(1L, "B1");
    when(releaseTaskMapper.selectDue(100)).thenReturn(List.of(task));
    when(releaseTaskMapper.claim(1L)).thenReturn(1);
    when(reservationService.release("B1")).thenReturn(true);

    scheduler.processDueTasks();

    verify(releaseTaskMapper).markDone(1L);
  }

  @Test
  void processDueTasks_失败后安排重试() {
    ReleaseTask task = task(1L, "B1");
    when(releaseTaskMapper.selectDue(100)).thenReturn(List.of(task));
    when(releaseTaskMapper.claim(1L)).thenReturn(1);
    when(reservationService.release("B1")).thenThrow(new IllegalStateException("db down"));

    scheduler.processDueTasks();

    verify(releaseTaskMapper).retry(eq(1L), any(LocalDateTime.class), contains("db down"));
  }

  private ReleaseTask task(Long id, String orderNo) {
    ReleaseTask task = new ReleaseTask();
    task.setId(id);
    task.setOrderNo(orderNo);
    task.setExecuteAt(LocalDateTime.now());
    task.setStatus(0);
    task.setAttempts(0);
    return task;
  }
}
