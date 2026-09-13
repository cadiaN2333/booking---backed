package com.example.booking.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.booking.common.UserContext;
import com.example.booking.domain.dto.CreateReservationRequest;
import com.example.booking.domain.entity.Court;
import com.example.booking.domain.entity.Reservation;
import com.example.booking.domain.entity.Slot;
import com.example.booking.domain.entity.Venue;
import com.example.booking.domain.enums.ReservationStatusEnum;
import com.example.booking.domain.vo.ReservationVO;
import com.example.booking.mapper.CourtMapper;
import com.example.booking.mapper.IdempotentMapper;
import com.example.booking.mapper.ReservationMapper;
import com.example.booking.mapper.SlotMapper;
import com.example.booking.mapper.VenueMapper;
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
  private VenueMapper venueMapper;
  private IdempotentMapper idempotentMapper;
  private ReleaseScheduler releaseScheduler;
  private SlotCacheService slotCacheService;
  private ReservationServiceImpl service;

  @BeforeEach
  void setUp() {
    reservationMapper = mock(ReservationMapper.class);
    slotMapper = mock(SlotMapper.class);
    courtMapper = mock(CourtMapper.class);
    venueMapper = mock(VenueMapper.class);
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
            slotCacheService,
            venueMapper);
    ReflectionTestUtils.setField(service, "lockMinutes", 15);
    UserContext.set(new UserContext.LoginUser(1L, "customer", "体验用户", 0));
    when(venueMapper.selectById(1L)).thenReturn(publicVenue());
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
    verify(releaseScheduler).finish("B1");
  }

  @Test
  void create_父场馆下架时不扣库存() {
    CreateReservationRequest request = request("token-offline");
    Venue offlineVenue = publicVenue();
    offlineVenue.setStatus(0);
    when(idempotentMapper.consume("token-offline", 1L, "RESERVATION")).thenReturn(1);
    when(slotMapper.selectById(1L)).thenReturn(slotWithAvailable(1));
    when(courtMapper.selectById(101L)).thenReturn(court());
    when(venueMapper.selectById(1L)).thenReturn(offlineVenue);

    assertThatThrownBy(() -> service.create(request))
        .hasMessageContaining("场地暂不可预约");

    verify(slotMapper, never()).deductAvailable(1L);
  }

  @Test
  void create_时段已开始时不扣库存() {
    CreateReservationRequest request = request("token-started");
    Slot started = slotWithAvailable(1);
    started.setStartAt(LocalDateTime.now().minusMinutes(1));
    when(idempotentMapper.consume("token-started", 1L, "RESERVATION")).thenReturn(1);
    when(slotMapper.selectById(1L)).thenReturn(started);
    when(courtMapper.selectById(101L)).thenReturn(court());

    assertThatThrownBy(() -> service.create(request))
        .hasMessageContaining("时段已开始");

    verify(slotMapper, never()).deductAvailable(1L);
  }

  private Slot slotWithAvailable(int available) {
    Slot slot = new Slot();
    slot.setId(1L);
    slot.setCourtId(101L);
    LocalDate date = LocalDate.now().plusDays(1);
    slot.setBizDate(date);
    slot.setStartAt(LocalDateTime.of(date, java.time.LocalTime.of(10, 0)));
    slot.setEndAt(LocalDateTime.of(date, java.time.LocalTime.of(11, 0)));
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
    court.setVenueId(1L);
    court.setName("测试场地");
    court.setPrice(5000);
    court.setAuditStatus(1);
    court.setStatus(1);
    return court;
  }

  private Venue publicVenue() {
    Venue venue = new Venue();
    venue.setId(1L);
    venue.setAuditStatus(1);
    venue.setStatus(1);
    return venue;
  }

  private CreateReservationRequest request(String token) {
    CreateReservationRequest request = new CreateReservationRequest();
    request.setToken(token);
    request.setSlotId(1L);
    return request;
  }
}
