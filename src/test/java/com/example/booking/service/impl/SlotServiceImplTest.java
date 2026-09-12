package com.example.booking.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.booking.domain.entity.Slot;
import com.example.booking.domain.vo.SlotVO;
import com.example.booking.mapper.CourtMapper;
import com.example.booking.mapper.ReservationMapper;
import com.example.booking.mapper.SlotMapper;
import com.example.booking.mapper.VenueMapper;
import com.example.booking.service.SlotCacheService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

class SlotServiceImplTest {

  private SlotMapper slotMapper;
  private SlotCacheService slotCacheService;
  private SlotServiceImpl service;

  @BeforeEach
  void setUp() {
    slotMapper = mock(SlotMapper.class);
    CourtMapper courtMapper = mock(CourtMapper.class);
    VenueMapper venueMapper = mock(VenueMapper.class);
    ReservationMapper reservationMapper = mock(ReservationMapper.class);
    slotCacheService = mock(SlotCacheService.class);
    service =
        new SlotServiceImpl(
            slotMapper, courtMapper, venueMapper, reservationMapper, slotCacheService);
  }

  @Test
  void listByCourtAndDate_缓存读取失败时回源数据库() {
    LocalDate date = LocalDate.of(2026, 9, 12);
    when(slotCacheService.getRawDay(101L, "2026-09-12"))
        .thenThrow(new RedisConnectionFailureException("redis down"));
    when(slotMapper.selectList(any())).thenReturn(List.of(slot()));

    List<SlotVO> result = service.listByCourtAndDate(101L, date);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(1L);
  }

  private Slot slot() {
    Slot slot = new Slot();
    slot.setId(1L);
    slot.setCourtId(101L);
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
