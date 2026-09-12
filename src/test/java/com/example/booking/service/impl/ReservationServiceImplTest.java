package com.example.booking.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.booking.common.UserContext;
import com.example.booking.domain.dto.CreateReservationRequest;
import com.example.booking.domain.entity.Court;
import com.example.booking.domain.entity.Reservation;
import com.example.booking.domain.entity.Slot;
import com.example.booking.domain.enums.ReservationStatusEnum;
import com.example.booking.domain.vo.ReservationVO;
import com.example.booking.mapper.CourtMapper;
import com.example.booking.mapper.IdempotentMapper;
import com.example.booking.mapper.ReservationMapper;
import com.example.booking.mapper.SlotMapper;
import com.example.booking.service.ReleaseScheduler;
import com.example.booking.service.SlotCacheService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReservationServiceImplTest {

  private ReservationMapper reservationMapper;
  private SlotMapper slotMapper;
  private CourtMapper courtMapper;
  private IdempotentMapper idempotentMapper;
  private ReleaseScheduler releaseScheduler;
  private SlotCacheService slotCacheService;
  private ReservationServiceImpl service;

  @BeforeEach
  void setUp() {
    reservationMapper = mock(ReservationMapper.class);
    slotMapper = mock(SlotMapper.class);
    courtMapper = mock(CourtMapper.class);
    idempotentMapper = mock(IdempotentMapper.class);
    releaseScheduler = mock(ReleaseScheduler.class);
    slotCacheService = mock(SlotCacheService.class);
    service =
        new ReservationServiceImpl(
            reservationMapper,
            slotMapper,
            courtMapper,
            idempotentMapper,
            releaseScheduler,
            slotCacheService);
    ReflectionTestUtils.setField(service, "lockMinutes", 15);
    UserContext.set(new UserContext.LoginUser(1L, "customer", "体验用户", 0));
  }

  @AfterEach
  void clearUserContext() {
    UserContext.clear();
  }

  @Test
  void create_只执行一次数据库库存扣减() {
    CreateReservationRequest request = new CreateReservationRequest();
    request.setToken("token-1");
    request.setSlotId(1L);
    when(idempotentMapper.consume("token-1", 1L, "RESERVATION")).thenReturn(1);
    when(reservationMapper.countActive(1L, 1L)).thenReturn(0L);
    when(slotCacheService.tryDeduct(1L)).thenReturn("OK");
    when(slotMapper.deductAvailable(1L)).thenReturn(1);
    when(slotMapper.selectById(1L)).thenReturn(slotWithAvailable(1));
    when(courtMapper.selectById(101L)).thenReturn(court());

    ReservationVO result = service.create(request);

    assertThat(result.getOrderNo()).isNotBlank();
    verify(slotMapper, times(1)).deductAvailable(1L);
  }

  @Test
  void create_Redis显示空库存时仍由数据库最终裁决() {
    CreateReservationRequest request = new CreateReservationRequest();
    request.setToken("token-2");
    request.setSlotId(1L);
    when(idempotentMapper.consume("token-2", 1L, "RESERVATION")).thenReturn(1);
    when(reservationMapper.countActive(1L, 1L)).thenReturn(0L);
    when(slotCacheService.tryDeduct(1L)).thenReturn("EMPTY");
    when(slotMapper.deductAvailable(1L)).thenReturn(1);
    when(slotMapper.selectById(1L)).thenReturn(slotWithAvailable(0));
    when(courtMapper.selectById(101L)).thenReturn(court());

    ReservationVO result = service.create(request);

    assertThat(result.getOrderNo()).isNotBlank();
    verify(slotMapper).deductAvailable(1L);
    verify(slotCacheService).alignStock(1L, 0);
  }

  @Test
  void cancel_释放后以数据库最新可用库存校准缓存() {
    Reservation reservation = pendingReservation();
    when(reservationMapper.selectByOrderNo("B1")).thenReturn(reservation);
    when(reservationMapper.updateStatus("B1", 0, ReservationStatusEnum.CANCELLED.getCode()))
        .thenReturn(1);
    when(slotMapper.selectById(1L)).thenReturn(slotWithAvailable(1));

    service.cancel("B1");

    verify(slotCacheService).alignStock(1L, 1);
  }

  private Slot slotWithAvailable(int available) {
    Slot slot = new Slot();
    slot.setId(1L);
    slot.setCourtId(101L);
    slot.setBizDate(LocalDate.of(2026, 9, 12));
    slot.setStartAt(LocalDateTime.of(2026, 9, 12, 10, 0));
    slot.setEndAt(LocalDateTime.of(2026, 9, 12, 11, 0));
    slot.setAvailable(available);
    return slot;
  }

  private Reservation pendingReservation() {
    Reservation reservation = new Reservation();
    reservation.setOrderNo("B1");
    reservation.setUserId(1L);
    reservation.setSlotId(1L);
    reservation.setCourtId(101L);
    reservation.setBizDate(LocalDate.of(2026, 9, 12));
    reservation.setStatus(ReservationStatusEnum.PENDING.getCode());
    return reservation;
  }

  private Court court() {
    Court court = new Court();
    court.setId(101L);
    court.setName("测试场地");
    court.setPrice(5000);
    return court;
  }
}
