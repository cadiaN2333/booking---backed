package com.example.booking.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.booking.common.BizException;
import com.example.booking.common.UserContext;
import com.example.booking.domain.entity.Court;
import com.example.booking.domain.entity.Venue;
import com.example.booking.domain.vo.AdminReviewVO;
import com.example.booking.mapper.CourtMapper;
import com.example.booking.mapper.VenueMapper;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdminAuditServiceTest {

  private static final long ADMIN_ID = 901L;
  private static final long VENUE_ID = 201L;
  private static final long COURT_ID = 101L;

  private VenueMapper venueMapper;
  private CourtMapper courtMapper;
  private AdminAuditServiceImpl service;

  @BeforeEach
  void setUp() {
    venueMapper = org.mockito.Mockito.mock(VenueMapper.class);
    courtMapper = org.mockito.Mockito.mock(CourtMapper.class);
    service = new AdminAuditServiceImpl(venueMapper, courtMapper);
    UserContext.set(new UserContext.LoginUser(ADMIN_ID, "admin", "管理员", 2));
  }

  @AfterEach
  void tearDown() {
    UserContext.clear();
  }

  @Test
  void 审核通过场馆自动上架并记录审核人和时间() {
    Venue venue = venue(VENUE_ID, 0, 0);
    venue.setAuditRemark("旧备注");
    when(venueMapper.selectById(VENUE_ID)).thenReturn(venue);

    service.approveVenue(VENUE_ID);

    assertThat(venue.getAuditStatus()).isEqualTo(1);
    assertThat(venue.getStatus()).isEqualTo(1);
    assertThat(venue.getAuditRemark()).isNull();
    assertThat(venue.getAuditBy()).isEqualTo(ADMIN_ID);
    assertThat(venue.getAuditTime()).isNotNull();
    verify(venueMapper).updateById(venue);
  }

  @Test
  void 驳回场地保留原因并保持下架() {
    Court court = court(COURT_ID, VENUE_ID, 0, 0);
    when(courtMapper.selectById(COURT_ID)).thenReturn(court);

    service.rejectCourt(COURT_ID, "营业时间不完整");

    assertThat(court.getAuditStatus()).isEqualTo(2);
    assertThat(court.getStatus()).isZero();
    assertThat(court.getAuditRemark()).isEqualTo("营业时间不完整");
    assertThat(court.getAuditBy()).isEqualTo(ADMIN_ID);
    assertThat(court.getAuditTime()).isNotNull();
    verify(courtMapper).updateById(court);
  }

  @Test
  void 非待审核资源不能重复审核() {
    Venue venue = venue(VENUE_ID, 1, 1);
    when(venueMapper.selectById(VENUE_ID)).thenReturn(venue);

    assertThatThrownBy(() -> service.approveVenue(VENUE_ID))
        .isInstanceOf(BizException.class)
        .hasMessage("场馆当前不是待审核状态，不能重复审核");
    verify(venueMapper, never()).updateById(any(Venue.class));
  }

  @Test
  void 场地父场馆未审核通过或未上架时不能通过() {
    Court court = court(COURT_ID, VENUE_ID, 0, 0);
    Venue venue = venue(VENUE_ID, 0, 1);
    when(courtMapper.selectById(COURT_ID)).thenReturn(court);
    when(venueMapper.selectById(VENUE_ID)).thenReturn(venue);

    assertThatThrownBy(() -> service.approveCourt(COURT_ID))
        .isInstanceOf(BizException.class)
        .hasMessage("场地所属场馆尚未审核通过并上架，不能审核场地");
    verify(courtMapper, never()).updateById(any(Court.class));
  }

  @Test
  void 查询只返回待审核资源并映射管理员审核视图() {
    Venue venue = venue(VENUE_ID, 0, 0);
    venue.setName("待审场馆");
    venue.setAddress("测试地址");
    venue.setMerchantId(301L);
    venue.setCreateTime(LocalDateTime.of(2026, 9, 13, 10, 0));
    when(venueMapper.selectList(any())).thenReturn(List.of(venue));

    List<AdminReviewVO> result = service.listReviews("venue", 0);

    assertThat(result).singleElement().satisfies(view -> {
      assertThat(view.getResourceType()).isEqualTo("venue");
      assertThat(view.getId()).isEqualTo(VENUE_ID);
      assertThat(view.getName()).isEqualTo("待审场馆");
      assertThat(view.getAddress()).isEqualTo("测试地址");
      assertThat(view.getMerchantId()).isEqualTo(301L);
      assertThat(view.getAuditStatus()).isZero();
    });
    verify(venueMapper).selectList(any());
  }

  @Test
  void 查询场地带父场馆名称且不直接返回实体() {
    Court court = court(COURT_ID, VENUE_ID, 0, 0);
    court.setName("一号场");
    court.setType("羽毛球");
    court.setPrice(8000);
    court.setOpenTime(LocalTime.of(9, 0));
    court.setCloseTime(LocalTime.of(21, 0));
    court.setSlotMinutes(30);
    Venue venue = venue(VENUE_ID, 1, 1);
    venue.setName("星辰馆");
    when(courtMapper.selectList(any())).thenReturn(List.of(court));
    when(venueMapper.selectById(VENUE_ID)).thenReturn(venue);

    List<AdminReviewVO> result = service.listReviews("court", 0);

    assertThat(result).singleElement().satisfies(view -> {
      assertThat(view.getResourceType()).isEqualTo("court");
      assertThat(view.getVenueId()).isEqualTo(VENUE_ID);
      assertThat(view.getVenueName()).isEqualTo("星辰馆");
      assertThat(view.getType()).isEqualTo("羽毛球");
      assertThat(view.getPrice()).isEqualTo(8000);
      assertThat(view.getSlotMinutes()).isEqualTo(30);
    });
    assertThat(result.get(0)).isNotInstanceOf(Court.class);
  }

  private Venue venue(long id, int status, int auditStatus) {
    Venue venue = new Venue();
    venue.setId(id);
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
