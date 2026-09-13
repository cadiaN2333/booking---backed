package com.example.booking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.booking.common.UserContext;
import com.example.booking.domain.entity.Venue;
import com.example.booking.mapper.VenueMapper;
import com.example.booking.service.VenueService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VenueServiceImpl implements VenueService {

  private final VenueMapper venueMapper;

  @Override
  public List<Venue> listOnline() {
    return venueMapper.selectList(
        new LambdaQueryWrapper<Venue>()
            .eq(Venue::getAuditStatus, 1)
            .eq(Venue::getStatus, 1)
            .orderByAsc(Venue::getId));
  }

  @Override
  public List<Venue> listMine() {
    return venueMapper.selectList(
        new LambdaQueryWrapper<Venue>()
            .eq(Venue::getMerchantId, UserContext.userId())
            .eq(Venue::getStatus, 1)
            .orderByAsc(Venue::getId));
  }

  @Override
  public Venue getById(Long venueId) {
    return venueMapper.selectById(venueId);
  }
}
