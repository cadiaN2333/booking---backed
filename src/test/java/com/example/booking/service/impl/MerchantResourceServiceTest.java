package com.example.booking.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.booking.common.BizException;
import com.example.booking.common.UserContext;
import com.example.booking.domain.dto.MerchantCourtRequest;
import com.example.booking.domain.dto.MerchantVenueRequest;
import com.example.booking.domain.entity.Court;
import com.example.booking.domain.entity.Venue;
import com.example.booking.mapper.CourtMapper;
import com.example.booking.mapper.VenueMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MerchantResourceServiceTest {

  private static final long MERCHANT_ID = 301L;
  private static final long OTHER_MERCHANT_ID = 302L;
  private static final long VENUE_ID = 201L;
  private static final long COURT_ID = 101L;

  private VenueMapper venueMapper;
  private CourtMapper courtMapper;
  private VenueServiceImpl venueService;
  private CourtServiceImpl courtService;

  @BeforeEach
  void setUp() {
    venueMapper = org.mockito.Mockito.mock(VenueMapper.class);
    courtMapper = org.mockito.Mockito.mock(CourtMapper.class);
    venueService = new VenueServiceImpl(venueMapper);
    courtService = new CourtServiceImpl(courtMapper, venueMapper);
    UserContext.set(new UserContext.LoginUser(MERCHANT_ID, "merchant", "商家", 1));
  }

  @AfterEach
  void tearDown() {
    UserContext.clear();
  }

  @Test
  void 创建场馆使用登录商家并写入待审下架默认状态() {
    MerchantVenueRequest request = new MerchantVenueRequest("新场馆", "测试地址");

    venueService.create(request);

    ArgumentCaptor<Venue> captor = ArgumentCaptor.forClass(Venue.class);
    verify(venueMapper).insert(captor.capture());
    Venue saved = captor.getValue();
    assertThat(saved.getMerchantId()).isEqualTo(MERCHANT_ID);
    assertThat(saved.getStatus()).isZero();
    assertThat(saved.getAuditStatus()).isZero();
    assertThat(saved.getName()).isEqualTo("新场馆");
    assertThat(saved.getAddress()).isEqualTo("测试地址");
  }

  @Test
  void 场馆列表包含本人待审驳回和下架资源() {
    Venue pending = venue(VENUE_ID, MERCHANT_ID, 0, 0);
    Venue rejected = venue(202L, MERCHANT_ID, 0, 2);
    Venue offline = venue(203L, MERCHANT_ID, 0, 1);
    when(venueMapper.selectList(any())).thenReturn(List.of(pending, rejected, offline));

    assertThat(venueService.listMine()).containsExactly(pending, rejected, offline);
  }

  @Test
  void 编辑场馆重置审核并自动下架() {
    Venue existing = venue(VENUE_ID, MERCHANT_ID, 1, 1);
    existing.setAuditRemark("旧备注");
    existing.setAuditTime(java.time.LocalDateTime.now());
    existing.setAuditBy(999L);
    when(venueMapper.selectById(VENUE_ID)).thenReturn(existing);

    venueService.update(VENUE_ID, new MerchantVenueRequest("修改后", "新地址"));

    assertThat(existing.getName()).isEqualTo("修改后");
    assertThat(existing.getAddress()).isEqualTo("新地址");
    assertThat(existing.getStatus()).isZero();
    assertThat(existing.getAuditStatus()).isZero();
    assertThat(existing.getAuditRemark()).isNull();
    assertThat(existing.getAuditTime()).isNull();
    assertThat(existing.getAuditBy()).isNull();
    verify(venueMapper).updateById(existing);
  }

  @Test
  void 场馆下架保留审核记录() {
    Venue existing = venue(VENUE_ID, MERCHANT_ID, 1, 1);
    existing.setAuditRemark("审核通过");
    existing.setAuditTime(java.time.LocalDateTime.now());
    existing.setAuditBy(999L);
    when(venueMapper.selectById(VENUE_ID)).thenReturn(existing);

    venueService.offline(VENUE_ID);

    assertThat(existing.getStatus()).isZero();
    assertThat(existing.getAuditStatus()).isEqualTo(1);
    assertThat(existing.getAuditRemark()).isEqualTo("审核通过");
    assertThat(existing.getAuditTime()).isNotNull();
    assertThat(existing.getAuditBy()).isEqualTo(999L);
    verify(venueMapper).updateById(existing);
  }

  @Test
  void 跨商家编辑场馆返回4030() {
    when(venueMapper.selectById(VENUE_ID)).thenReturn(venue(VENUE_ID, OTHER_MERCHANT_ID, 1, 1));

    assertThatThrownBy(
            () -> venueService.update(VENUE_ID, new MerchantVenueRequest("修改", "地址")))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 4030);
    verify(venueMapper, never()).updateById(any(Venue.class));
  }

  @Test
  void 创建场地必须使用本人场馆且写入待审下架默认状态() {
    when(venueMapper.selectById(VENUE_ID)).thenReturn(venue(VENUE_ID, MERCHANT_ID, 0, 0));
    MerchantCourtRequest request = courtRequest(VENUE_ID);

    courtService.create(request);

    ArgumentCaptor<Court> captor = ArgumentCaptor.forClass(Court.class);
    verify(courtMapper).insert(captor.capture());
    Court saved = captor.getValue();
    assertThat(saved.getVenueId()).isEqualTo(VENUE_ID);
    assertThat(saved.getStatus()).isZero();
    assertThat(saved.getAuditStatus()).isZero();
    assertThat(saved.getPrice()).isEqualTo(5000);
  }

  @Test
  void 创建场地的父场馆属于其他商家时返回4030() {
    when(venueMapper.selectById(VENUE_ID)).thenReturn(venue(VENUE_ID, OTHER_MERCHANT_ID, 1, 1));

    assertThatThrownBy(() -> courtService.create(courtRequest(VENUE_ID)))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 4030);
    verify(courtMapper, never()).insert(any(Court.class));
  }

  @Test
  void 编辑下架场地仍允许并重置审核() {
    Court existing = court(COURT_ID, VENUE_ID, 0, 2);
    existing.setAuditRemark("旧驳回");
    existing.setAuditTime(java.time.LocalDateTime.now());
    existing.setAuditBy(999L);
    when(courtMapper.selectById(COURT_ID)).thenReturn(existing);
    when(venueMapper.selectById(VENUE_ID)).thenReturn(venue(VENUE_ID, MERCHANT_ID, 0, 0));

    courtService.update(COURT_ID, courtRequest(VENUE_ID));

    assertThat(existing.getStatus()).isZero();
    assertThat(existing.getAuditStatus()).isZero();
    assertThat(existing.getAuditRemark()).isNull();
    assertThat(existing.getAuditTime()).isNull();
    assertThat(existing.getAuditBy()).isNull();
    verify(courtMapper).updateById(existing);
  }

  @Test
  void 场地下架保留审核记录() {
    Court existing = court(COURT_ID, VENUE_ID, 1, 1);
    existing.setAuditRemark("审核通过");
    existing.setAuditTime(java.time.LocalDateTime.now());
    existing.setAuditBy(999L);
    when(courtMapper.selectById(COURT_ID)).thenReturn(existing);
    when(venueMapper.selectById(VENUE_ID)).thenReturn(venue(VENUE_ID, MERCHANT_ID, 1, 1));

    courtService.offline(COURT_ID);

    assertThat(existing.getStatus()).isZero();
    assertThat(existing.getAuditStatus()).isEqualTo(1);
    assertThat(existing.getAuditRemark()).isEqualTo("审核通过");
    assertThat(existing.getAuditTime()).isNotNull();
    assertThat(existing.getAuditBy()).isEqualTo(999L);
    verify(courtMapper).updateById(existing);
  }

  @Test
  void 跨商家读取下架场地返回4030而不是场地不存在() {
    when(courtMapper.selectById(COURT_ID)).thenReturn(court(COURT_ID, VENUE_ID, 0, 0));
    when(venueMapper.selectById(VENUE_ID)).thenReturn(venue(VENUE_ID, OTHER_MERCHANT_ID, 0, 0));

    assertThatThrownBy(() -> courtService.getMine(COURT_ID))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 4030);
  }

  @Test
  void DTO校验拒绝非法价格时间和时段边界() {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    MerchantCourtRequest request = courtRequest(VENUE_ID);
    request.setPrice(0);
    request.setOpenTime(LocalTime.of(20, 0));
    request.setCloseTime(LocalTime.of(9, 0));
    request.setSlotMinutes(14);

    assertThat(validator.validate(request))
        .extracting(Object::toString)
        .anyMatch(message -> message.contains("价格必须大于0"))
        .anyMatch(message -> message.contains("营业开始时间必须早于结束时间"))
        .anyMatch(message -> message.contains("时段长度必须在15至240分钟之间"));
  }

  @Test
  void DTO校验接受15和240分钟但拒绝非15倍数() {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    MerchantCourtRequest request = courtRequest(VENUE_ID);

    request.setSlotMinutes(15);
    assertThat(validator.validate(request)).isEmpty();
    request.setSlotMinutes(240);
    assertThat(validator.validate(request)).isEmpty();
    request.setSlotMinutes(16);
    assertThat(validator.validate(request))
        .extracting(Object::toString)
        .anyMatch(message -> message.contains("时段长度必须是15分钟的倍数"));
  }

  private MerchantCourtRequest courtRequest(Long venueId) {
    return new MerchantCourtRequest(
        venueId, "一号场地", "羽毛球", 5000, LocalTime.of(9, 0), LocalTime.of(21, 0), 30);
  }

  private Venue venue(long id, long merchantId, int status, int auditStatus) {
    Venue venue = new Venue();
    venue.setId(id);
    venue.setMerchantId(merchantId);
    venue.setStatus(status);
    venue.setAuditStatus(auditStatus);
    return venue;
  }

  private Court court(long id, long venueId, int status, int auditStatus) {
    Court court = new Court();
    court.setId(id);
    court.setVenueId(venueId);
    court.setStatus(status);
    court.setAuditStatus(auditStatus);
    return court;
  }
}
