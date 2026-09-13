package com.example.booking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.booking.common.UserContext;
import com.example.booking.common.BizException;
import com.example.booking.domain.dto.MerchantVenueRequest;
import com.example.booking.domain.entity.Venue;
import com.example.booking.mapper.VenueMapper;
import com.example.booking.service.VenueService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            .orderByAsc(Venue::getId));
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public Venue create(MerchantVenueRequest request) {
    Venue venue = new Venue();
    venue.setName(request.name());
    venue.setAddress(request.address());
    venue.setMerchantId(UserContext.userId());
    venue.setStatus(0);
    venue.setAuditStatus(0);
    venue.setAuditRemark(null);
    venue.setAuditTime(null);
    venue.setAuditBy(null);
    venueMapper.insert(venue);
    return venue;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public Venue update(Long venueId, MerchantVenueRequest request) {
    Venue venue = getMine(venueId);
    venue.setName(request.name());
    venue.setAddress(request.address());
    resetForReview(venue);
    venueMapper.updateById(venue);
    return venue;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void offline(Long venueId) {
    Venue venue = getMine(venueId);
    venue.setStatus(0);
    venueMapper.updateById(venue);
  }

  @Override
  public Venue getMine(Long venueId) {
    Venue venue = venueMapper.selectById(venueId);
    if (venue == null) {
      throw new BizException("场馆不存在");
    }
    if (!UserContext.userId().equals(venue.getMerchantId())) {
      throw new BizException(4030, "无权操作该场馆");
    }
    return venue;
  }

  private void resetForReview(Venue venue) {
    venue.setStatus(0);
    venue.setAuditStatus(0);
    venue.setAuditRemark(null);
    venue.setAuditTime(null);
    venue.setAuditBy(null);
  }

  @Override
  public Venue getById(Long venueId) {
    return venueMapper.selectById(venueId);
  }
}
