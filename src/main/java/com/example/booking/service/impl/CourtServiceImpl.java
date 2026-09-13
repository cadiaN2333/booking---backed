package com.example.booking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.booking.common.UserContext;
import com.example.booking.common.BizException;
import com.example.booking.domain.dto.MerchantCourtRequest;
import com.example.booking.domain.entity.Court;
import com.example.booking.domain.entity.Venue;
import com.example.booking.mapper.CourtMapper;
import com.example.booking.mapper.VenueMapper;
import com.example.booking.service.CourtService;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourtServiceImpl implements CourtService {

  private final CourtMapper courtMapper;
  private final VenueMapper venueMapper;

  @Override
  public List<Court> listOnlineByVenue(Long venueId) {
    List<Long> publicVenueIds =
        venueMapper
            .selectList(
                new LambdaQueryWrapper<Venue>()
                    .eq(Venue::getAuditStatus, 1)
                    .eq(Venue::getStatus, 1))
            .stream()
            .map(Venue::getId)
            .filter(Objects::nonNull)
            .toList();
    if (publicVenueIds.isEmpty()) {
      return List.of();
    }
    LambdaQueryWrapper<Court> q =
        new LambdaQueryWrapper<Court>()
            .eq(Court::getAuditStatus, 1)
            .eq(Court::getStatus, 1)
            .in(Court::getVenueId, publicVenueIds);
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
  @Transactional(rollbackFor = Exception.class)
  public Court create(MerchantCourtRequest request) {
    requireMineVenue(request.getVenueId());
    Court court = new Court();
    copyFields(request, court);
    court.setStatus(0);
    court.setAuditStatus(0);
    court.setAuditRemark(null);
    court.setAuditTime(null);
    court.setAuditBy(null);
    courtMapper.insert(court);
    return court;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public Court update(Long courtId, MerchantCourtRequest request) {
    Court court = getMine(courtId);
    if (!Objects.equals(request.getVenueId(), court.getVenueId())) {
      throw new BizException("编辑场地时不能修改所属场馆");
    }
    requireMineVenue(request.getVenueId());
    copyFields(request, court);
    resetForReview(court);
    courtMapper.updateById(court);
    return court;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void offline(Long courtId) {
    Court court = getMine(courtId);
    court.setStatus(0);
    courtMapper.updateById(court);
  }

  @Override
  public Court getMine(Long courtId) {
    Court court = courtMapper.selectById(courtId);
    if (court == null) {
      throw new BizException("场地不存在");
    }
    Venue venue = venueMapper.selectById(court.getVenueId());
    if (venue == null || !UserContext.userId().equals(venue.getMerchantId())) {
      throw new BizException(4030, "无权操作该场地");
    }
    return court;
  }

  private Venue requireMineVenue(Long venueId) {
    Venue venue = venueMapper.selectById(venueId);
    if (venue == null) {
      throw new BizException("场馆不存在");
    }
    if (!UserContext.userId().equals(venue.getMerchantId())) {
      throw new BizException(4030, "无权操作该场馆下的场地");
    }
    return venue;
  }

  private void copyFields(MerchantCourtRequest request, Court court) {
    court.setVenueId(request.getVenueId());
    court.setName(request.getName());
    court.setType(request.getType());
    court.setPrice(request.getPrice());
    court.setOpenTime(request.getOpenTime());
    court.setCloseTime(request.getCloseTime());
    court.setSlotMinutes(request.getSlotMinutes());
  }

  private void resetForReview(Court court) {
    court.setStatus(0);
    court.setAuditStatus(0);
    court.setAuditRemark(null);
    court.setAuditTime(null);
    court.setAuditBy(null);
  }
}
