package com.example.booking.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

class SlotServiceImplTest {

  private static final long COURT_ID = 101L;
  private static final long VENUE_ID = 201L;
  private static final long MERCHANT_ID = 301L;

  private SlotMapper slotMapper;
  private CourtMapper courtMapper;
  private VenueMapper venueMapper;
  private SlotCacheService slotCacheService;
  private SlotServiceImpl service;

  @BeforeEach
  void setUp() {
    slotMapper = mock(SlotMapper.class);
    courtMapper = mock(CourtMapper.class);
    venueMapper = mock(VenueMapper.class);
    ReservationMapper reservationMapper = mock(ReservationMapper.class);
    slotCacheService = mock(SlotCacheService.class);
    service =
        new SlotServiceImpl(
            slotMapper, courtMapper, venueMapper, reservationMapper, slotCacheService);
  }

  @AfterEach
  void tearDown() {
    UserContext.clear();
  }

  @Test
  void listByCourtAndDate_缓存读取失败时回源数据库() {
    LocalDate date = LocalDate.of(2026, 9, 12);
    givenResource(1, 1, 1, 1);
    when(slotCacheService.getRawDay(COURT_ID, "2026-09-12"))
        .thenThrow(new RedisConnectionFailureException("redis down"));
    when(slotMapper.selectList(any())).thenReturn(List.of(slot()));

    List<SlotVO> result = service.listByCourtAndDate(COURT_ID, date);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(1L);
  }

  @Test
  void listByCourtAndDate_场地下架时不返回排期且不读缓存() {
    givenResource(1, 0, 1, 1);

    List<SlotVO> result = service.listByCourtAndDate(COURT_ID, LocalDate.of(2026, 9, 12));

    assertThat(result).isEmpty();
    verify(slotCacheService, never()).getRawDay(any(), any());
  }

  @Test
  void listByCourtAndDate_场地待审时不返回排期且不读缓存() {
    givenResource(0, 1, 1, 1);

    List<SlotVO> result = service.listByCourtAndDate(COURT_ID, LocalDate.of(2026, 9, 12));

    assertThat(result).isEmpty();
    verify(slotCacheService, never()).getRawDay(any(), any());
  }

  @Test
  void listByCourtAndDate_场馆下架时不返回排期且不读缓存() {
    givenResource(1, 1, 1, 0);

    List<SlotVO> result = service.listByCourtAndDate(COURT_ID, LocalDate.of(2026, 9, 12));

    assertThat(result).isEmpty();
    verify(slotCacheService, never()).getRawDay(any(), any());
  }

  @Test
  void listByCourtAndDate_场馆待审时不返回排期且不读缓存() {
    givenResource(1, 1, 0, 1);

    List<SlotVO> result = service.listByCourtAndDate(COURT_ID, LocalDate.of(2026, 9, 12));

    assertThat(result).isEmpty();
    verify(slotCacheService, never()).getRawDay(any(), any());
  }

  @Test
  void generateForMerchant_场地下架时拒绝生成排期() {
    assertGenerateRejected(court(1, 0), venue(1, 1));
  }

  @Test
  void generateForMerchant_场地待审时拒绝生成排期() {
    assertGenerateRejected(court(0, 1), venue(1, 1));
  }

  @Test
  void generateForMerchant_场馆下架时拒绝生成排期() {
    assertGenerateRejected(court(1, 1), venue(1, 0));
  }

  @Test
  void generateForMerchant_场馆待审时拒绝生成排期() {
    assertGenerateRejected(court(1, 1), venue(0, 1));
  }

  private void assertGenerateRejected(Court court, Venue venue) {
    when(courtMapper.selectById(COURT_ID)).thenReturn(court);
    when(venueMapper.selectById(VENUE_ID)).thenReturn(venue);
    UserContext.set(new UserContext.LoginUser(MERCHANT_ID, "merchant", "商家", 2));

    assertThatThrownBy(() -> service.generateForMerchant(COURT_ID))
        .isInstanceOf(BizException.class)
        .hasMessage("场地未审核通过或未上架")
        .isInstanceOfSatisfying(
            BizException.class, exception -> assertThat(exception.getCode()).isEqualTo(4030));
    verify(slotMapper, never()).selectList(any());
    verify(slotMapper, never()).insert(any(Slot.class));
  }

  private void givenResource(int courtAuditStatus, int courtStatus, int venueAuditStatus, int venueStatus) {
    when(courtMapper.selectById(COURT_ID)).thenReturn(court(courtAuditStatus, courtStatus));
    when(venueMapper.selectById(VENUE_ID)).thenReturn(venue(venueAuditStatus, venueStatus));
  }

  private Court court(int auditStatus, int status) {
    Court court = new Court();
    court.setId(COURT_ID);
    court.setVenueId(VENUE_ID);
    court.setPrice(0);
    court.setAuditStatus(auditStatus);
    court.setStatus(status);
    return court;
  }

  private Venue venue(int auditStatus, int status) {
    Venue venue = new Venue();
    venue.setId(VENUE_ID);
    venue.setMerchantId(MERCHANT_ID);
    venue.setAuditStatus(auditStatus);
    venue.setStatus(status);
    return venue;
  }

  private Slot slot() {
    Slot slot = new Slot();
    slot.setId(1L);
    slot.setCourtId(COURT_ID);
    slot.setBizDate(LocalDate.of(2026, 9, 12));
    slot.setStartAt(LocalDateTime.of(2026, 9, 12, 10, 0));
    slot.setEndAt(LocalDateTime.of(2026, 9, 12, 11, 0));
    slot.setTotal(1);
    slot.setAvailable(1);
    slot.setLocked(0);
    slot.setSold(0);
    return slot;
  }
}
