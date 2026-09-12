package com.example.booking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.booking.common.UserContext;
import com.example.booking.common.BizException;
import com.example.booking.domain.entity.Court;
import com.example.booking.domain.entity.Venue;
import com.example.booking.mapper.CourtMapper;
import com.example.booking.mapper.VenueMapper;
import com.example.booking.service.CourtService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourtServiceImpl implements CourtService {

  private final CourtMapper courtMapper;
  private final VenueMapper venueMapper;

  @Override
  public List<Court> listOnlineByVenue(Long venueId) {
    LambdaQueryWrapper<Court> q = new LambdaQueryWrapper<Court>().eq(Court::getStatus, 1);
    if (venueId != null) {
      q.eq(Court::getVenueId, venueId);
    }
    return courtMapper.selectList(q.orderByAsc(Court::getId));
  }

  @Override
  public List<Court> listMine() {
    List<Long> venueIds =
        venueMapper
            .selectList(new LambdaQueryWrapper<Venue>().eq(Venue::getMerchantId, UserContext.userId()))
            .stream()
            .map(Venue::getId)
            .toList();
    if (venueIds.isEmpty()) {
      return List.of();
    }
    return courtMapper.selectList(
        new LambdaQueryWrapper<Court>().in(Court::getVenueId, venueIds).orderByAsc(Court::getId));
  }

  @Override
  public Court getMine(Long courtId) {
    Court court = courtMapper.selectById(courtId);
    if (court == null || !Integer.valueOf(1).equals(court.getStatus())) {
      throw new BizException("场地不存在");
    }
    Venue venue = venueMapper.selectById(court.getVenueId());
    if (venue == null || !UserContext.userId().equals(venue.getMerchantId())) {
      throw new BizException(4030, "无权操作该场地");
    }
    return court;
  }
}
