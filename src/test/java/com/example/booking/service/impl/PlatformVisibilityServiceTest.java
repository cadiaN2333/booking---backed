package com.example.booking.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.booking.domain.entity.Court;
import com.example.booking.domain.entity.Venue;
import com.example.booking.mapper.CourtMapper;
import com.example.booking.mapper.ReservationMapper;
import com.example.booking.mapper.SlotMapper;
import com.example.booking.mapper.VenueMapper;
import com.example.booking.service.SlotCacheService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlatformVisibilityServiceTest {

  @Test
  void 顾客场馆列表同时过滤审核通过和已上架() {
    VenueMapper mapper = mock(VenueMapper.class);
    when(mapper.selectList(any())).thenReturn(List.of());

    new VenueServiceImpl(mapper).listOnline();

    ArgumentCaptor<LambdaQueryWrapper<Venue>> captor =
        ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(mapper).selectList(captor.capture());
    assertThat(captor.getValue().getExpression().getNormal()).hasSizeGreaterThan(3);
  }

  @Test
  void 顾客场地列表同时过滤审核通过和已上架() {
    CourtMapper mapper = mock(CourtMapper.class);
    when(mapper.selectList(any())).thenReturn(List.of());

    new CourtServiceImpl(mapper, mock(VenueMapper.class)).listOnlineByVenue(1L);

    ArgumentCaptor<LambdaQueryWrapper<Court>> captor =
        ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(mapper).selectList(captor.capture());
    assertThat(captor.getValue().getExpression().getNormal()).hasSizeGreaterThan(5);
  }

  @Test
  void 全量生成时段只扫描审核通过且已上架场地() {
    CourtMapper mapper = mock(CourtMapper.class);
    when(mapper.selectList(any())).thenReturn(List.of());
    SlotServiceImpl service = new SlotServiceImpl(
        mock(SlotMapper.class), mapper, mock(VenueMapper.class), mock(ReservationMapper.class),
        mock(SlotCacheService.class));

    service.generateAll(14);

    ArgumentCaptor<LambdaQueryWrapper<Court>> captor =
        ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(mapper).selectList(captor.capture());
    assertThat(captor.getValue().getExpression().getNormal()).hasSizeGreaterThan(3);
  }
}
