package com.example.booking.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.booking.domain.entity.Court;
import com.example.booking.domain.entity.Venue;
import com.example.booking.domain.vo.SlotVO;
import com.example.booking.service.CourtService;
import com.example.booking.service.SlotService;
import com.example.booking.service.VenueService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GlobalRecommendationServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 13);
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneId.of("UTC"));

  private VenueService venueService;
  private CourtService courtService;
  private SlotService slotService;
  private RecommendationSemanticService semanticService;
  private GlobalRecommendationService service;

  @BeforeEach
  void setUp() {
    venueService = mock(VenueService.class);
    courtService = mock(CourtService.class);
    slotService = mock(SlotService.class);
    semanticService = mock(RecommendationSemanticService.class);
    service =
        new GlobalRecommendationService(
            venueService, courtService, slotService, semanticService, FIXED_CLOCK);
  }

  @Test
  void search_只返回审核通过且上架资源的有余量时段() {
    Venue publicVenue = venue(1L, "星辰羽毛球馆", 1, 1);
    Venue offlineVenue = venue(2L, "下架场馆", 1, 0);
    Court publicCourt = court(101L, 1L, "1 号场", "羽毛球", 8000, 1, 1);
    Court offlineVenueCourt = court(201L, 2L, "会议室", "会议室", 12000, 1, 1);
    Court pendingCourt = court(102L, 1L, "待审场", "羽毛球", 8000, 0, 1);

    when(venueService.listOnline()).thenReturn(List.of(publicVenue, offlineVenue));
    when(courtService.listOnlineByVenue(null))
        .thenReturn(List.of(publicCourt, offlineVenueCourt, pendingCourt));
    when(slotService.listByCourtAndDate(101L, TODAY))
        .thenReturn(
            List.of(
                slot(1L, 101L, TODAY, "09:00", 0, 8000),
                slot(2L, 101L, TODAY, "10:00", 2, 8000),
                slot(3L, 999L, TODAY, "11:00", 2, 8000),
                slot(4L, 101L, TODAY.plusDays(1), "12:00", 2, 8000)));

    GlobalRecommendationResponse result =
        service.search(new GlobalRecommendationRequest(null, TODAY, null, null, null, null, 5));

    assertThat(result.recommendations()).extracting(GlobalRecommendationItem::slotId).containsExactly(2L);
    assertThat(result.recommendations().get(0))
        .extracting(
            GlobalRecommendationItem::venueId,
            GlobalRecommendationItem::venueName,
            GlobalRecommendationItem::venueAddress,
            GlobalRecommendationItem::courtId,
            GlobalRecommendationItem::courtName,
            GlobalRecommendationItem::courtType,
            GlobalRecommendationItem::slotId,
            GlobalRecommendationItem::price,
            GlobalRecommendationItem::available,
            GlobalRecommendationItem::score)
        .containsExactly(
            1L,
            "星辰羽毛球馆",
            "天河区体育西路 88 号",
            101L,
            "1 号场",
            "羽毛球",
            2L,
            8000,
            2,
            0L);
    assertThat(result.degraded()).isTrue();
    verify(slotService).listByCourtAndDate(101L, TODAY);
    verify(semanticService).enhance(any(), eq(101L), eq(null));
  }

  @Test
  void search_结构化条件过滤类型时间和价格并限制最多五条() {
    Venue venue = venue(1L, "星辰羽毛球馆", 1, 1);
    Court badminton = court(101L, 1L, "普通场", "羽毛球", 8000, 1, 1);
    Court meeting = court(102L, 1L, "会议室", "会议室", 9000, 1, 1);
    Court expensiveBadminton = court(103L, 1L, "VIP 场", "羽毛球", 15000, 1, 1);
    when(venueService.listOnline()).thenReturn(List.of(venue));
    when(courtService.listOnlineByVenue(null)).thenReturn(List.of(badminton, meeting, expensiveBadminton));
    when(slotService.listByCourtAndDate(any(), eq(TODAY)))
        .thenAnswer(
            invocation ->
                List.of(
                    slot(
                        invocation.getArgument(0, Long.class),
                        invocation.getArgument(0, Long.class),
                        TODAY,
                        "18:00",
                        3,
                        invocation.getArgument(0, Long.class) == 103L ? 15000 : 9000)));

    GlobalRecommendationResponse result =
        service.search(
            new GlobalRecommendationRequest(
                "",
                TODAY,
                "羽毛球",
                LocalTime.of(18, 0),
                LocalTime.of(22, 0),
                10000,
                5));

    assertThat(result.recommendations()).extracting(GlobalRecommendationItem::courtId).containsExactly(101L);
    assertThat(result.recommendations().get(0).startAt()).isEqualTo(LocalDateTime.of(TODAY, LocalTime.of(18, 0)));
  }

  @Test
  void search_查询预算作为硬过滤而不是只参与评分() {
    Venue venue = venue(1L, "星辰羽毛球馆", 1, 1);
    Court court = court(101L, 1L, "普通场", "羽毛球", 8000, 1, 1);
    SlotVO withinBudget = slot(1L, 101L, TODAY, "18:00", 2, 10000);
    SlotVO overBudget = slot(2L, 101L, TODAY, "19:00", 2, 10001);
    SlotVO missingPrice = slot(3L, 101L, TODAY, "20:00", 2, 10000);
    missingPrice.setPrice(null);
    when(venueService.listOnline()).thenReturn(List.of(venue));
    when(courtService.listOnlineByVenue(null)).thenReturn(List.of(court));
    when(slotService.listByCourtAndDate(101L, TODAY))
        .thenReturn(List.of(withinBudget, overBudget, missingPrice));

    GlobalRecommendationResponse result =
        service.search(
            new GlobalRecommendationRequest(
                "预算100元以内", TODAY, null, null, null, null, 5));

    assertThat(result.recommendations()).extracting(GlobalRecommendationItem::slotId).containsExactly(1L);
    assertThat(result.recommendations()).allMatch(item -> item.price() != null && item.price() <= 10000);
  }

  @Test
  void search_查询文本中的日期类型时间和预算参与规则排序且日期覆盖请求日期() {
    Venue venue = venue(1L, "星辰羽毛球馆", 1, 1);
    Court badminton = court(101L, 1L, "普通场", "羽毛球", 8000, 1, 1);
    Court meeting = court(102L, 1L, "会议室", "会议室", 6000, 1, 1);
    when(venueService.listOnline()).thenReturn(List.of(venue));
    when(courtService.listOnlineByVenue(null)).thenReturn(List.of(badminton, meeting));
    when(slotService.listByCourtAndDate(101L, TODAY.plusDays(2)))
        .thenReturn(
            List.of(
                slot(1L, 101L, TODAY.plusDays(2), "10:00", 2, 7000),
                slot(2L, 101L, TODAY.plusDays(2), "19:00", 1, 8000)));
    when(slotService.listByCourtAndDate(102L, TODAY.plusDays(2)))
        .thenReturn(List.of(slot(3L, 102L, TODAY.plusDays(2), "19:00", 3, 6000)));

    GlobalRecommendationResponse result =
        service.search(
            new GlobalRecommendationRequest(
                "后天晚上找羽毛球，预算100元以内",
                TODAY,
                null,
                null,
                null,
                null,
                5));

    verify(slotService).listByCourtAndDate(101L, TODAY.plusDays(2));
    verify(slotService).listByCourtAndDate(102L, TODAY.plusDays(2));
    assertThat(result.recommendations()).extracting(GlobalRecommendationItem::slotId).containsExactly(2L, 1L, 3L);
    assertThat(result.recommendations().get(0).date()).isEqualTo(TODAY.plusDays(2));
    assertThat(result.recommendations().get(0).tags()).contains(RecommendationTag.EVENING);
  }

  @Test
  void search_查询文本中的明确日期覆盖结构化日期() {
    LocalDate queryDate = TODAY.plusDays(3);
    Venue venue = venue(1L, "星辰羽毛球馆", 1, 1);
    Court court = court(101L, 1L, "普通场", "羽毛球", 8000, 1, 1);
    when(venueService.listOnline()).thenReturn(List.of(venue));
    when(courtService.listOnlineByVenue(null)).thenReturn(List.of(court));
    when(slotService.listByCourtAndDate(101L, queryDate))
        .thenReturn(List.of(slot(1L, 101L, queryDate, "10:00", 2, 8000)));

    GlobalRecommendationResponse result =
        service.search(
            new GlobalRecommendationRequest(
                "请安排 2026-09-16 的羽毛球场",
                TODAY,
                null,
                null,
                null,
                null,
                5));

    verify(slotService).listByCourtAndDate(101L, queryDate);
    assertThat(result.recommendations().get(0).date()).isEqualTo(queryDate);
  }

  @Test
  void search_未提供日期时默认当天() {
    Venue venue = venue(1L, "星辰羽毛球馆", 1, 1);
    Court court = court(101L, 1L, "普通场", "羽毛球", 8000, 1, 1);
    when(venueService.listOnline()).thenReturn(List.of(venue));
    when(courtService.listOnlineByVenue(null)).thenReturn(List.of(court));
    when(slotService.listByCourtAndDate(101L, TODAY)).thenReturn(List.of());

    service.search(new GlobalRecommendationRequest(null, null, null, null, null, null, null));

    verify(slotService).listByCourtAndDate(101L, TODAY);
  }

  @Test
  void search_指定历史日期统一返回空结果且不读取推荐资源() {
    GlobalRecommendationResponse result =
        service.search(
            new GlobalRecommendationRequest(
                null, TODAY.minusDays(1), null, null, null, null, 5));

    assertThat(result.recommendations()).isEmpty();
    assertThat(result.degraded()).isTrue();
    verifyNoInteractions(venueService, courtService, slotService, semanticService);
  }

  @Test
  void search_候选按时段编号去重并保留规则排序更优快照() {
    Venue venue = venue(1L, "星辰羽毛球馆", 1, 1);
    Court court = court(101L, 1L, "普通场", "羽毛球", 8000, 1, 1);
    when(venueService.listOnline()).thenReturn(List.of(venue));
    when(courtService.listOnlineByVenue(null)).thenReturn(List.of(court));
    when(slotService.listByCourtAndDate(101L, TODAY))
        .thenReturn(
            List.of(
                slot(1L, 101L, TODAY, "19:00", 1, 9000),
                slot(1L, 101L, TODAY, "18:00", 5, 8000),
                slot(2L, 101L, TODAY, "20:00", 2, 8000)));

    GlobalRecommendationResponse result =
        service.search(new GlobalRecommendationRequest(null, TODAY, null, null, null, null, 2));

    assertThat(result.recommendations()).extracting(GlobalRecommendationItem::slotId).containsExactly(1L, 2L);
    assertThat(result.recommendations().get(0))
        .extracting(
            GlobalRecommendationItem::startAt,
            GlobalRecommendationItem::price,
            GlobalRecommendationItem::available)
        .containsExactly(LocalDateTime.of(TODAY, LocalTime.of(18, 0)), 8000, 5);
  }

  @Test
  void search_今晚七点后覆盖十九点到当天结束() {
    Venue venue = venue(1L, "星辰羽毛球馆", 1, 1);
    Court court = court(101L, 1L, "普通场", "羽毛球", 8000, 1, 1);
    when(venueService.listOnline()).thenReturn(List.of(venue));
    when(courtService.listOnlineByVenue(null)).thenReturn(List.of(court));
    when(slotService.listByCourtAndDate(101L, TODAY))
        .thenReturn(
            List.of(
                slot(1L, 101L, TODAY, "18:00", 2, 8000),
                slot(2L, 101L, TODAY, "19:00", 2, 8000),
                slot(3L, 101L, TODAY, "22:00", 2, 8000)));

    GlobalRecommendationResponse result =
        service.search(new GlobalRecommendationRequest("今晚7点后", TODAY, null, null, null, null, 5));

    assertThat(result.recommendations()).extracting(GlobalRecommendationItem::slotId).containsExactly(2L, 3L);
  }

  @Test
  void search_今天不返回已经过去的时段() {
    Clock noonClock =
        Clock.fixed(Instant.parse("2026-09-13T12:30:00Z"), ZoneId.of("UTC"));
    service = new GlobalRecommendationService(venueService, courtService, slotService, semanticService, noonClock);
    Venue venue = venue(1L, "星辰羽毛球馆", 1, 1);
    Court court = court(101L, 1L, "普通场", "羽毛球", 8000, 1, 1);
    when(venueService.listOnline()).thenReturn(List.of(venue));
    when(courtService.listOnlineByVenue(null)).thenReturn(List.of(court));
    when(slotService.listByCourtAndDate(101L, TODAY))
        .thenReturn(
            List.of(
                slot(1L, 101L, TODAY, "11:00", 2, 8000),
                slot(2L, 101L, TODAY, "13:00", 2, 8000)));

    GlobalRecommendationResponse result =
        service.search(new GlobalRecommendationRequest(null, TODAY, null, null, null, null, 5));

    assertThat(result.recommendations()).extracting(GlobalRecommendationItem::slotId).containsExactly(2L);
  }

  @Test
  void search_语义服务异常时保留规则候选并标记降级() {
    Venue venue = venue(1L, "星辰羽毛球馆", 1, 1);
    Court court = court(101L, 1L, "普通场", "羽毛球", 8000, 1, 1);
    when(venueService.listOnline()).thenReturn(List.of(venue));
    when(courtService.listOnlineByVenue(null)).thenReturn(List.of(court));
    when(slotService.listByCourtAndDate(101L, TODAY))
        .thenReturn(List.of(slot(1L, 101L, TODAY, "18:00", 2, 8000)));
    when(semanticService.enhance(any(), eq(101L), eq("安静一点")))
        .thenThrow(new IllegalStateException("vector down"));

    GlobalRecommendationResponse result =
        service.search(new GlobalRecommendationRequest("安静一点", TODAY, null, null, null, null, 5));

    assertThat(result.degraded()).isTrue();
    assertThat(result.recommendations()).extracting(GlobalRecommendationItem::slotId).containsExactly(1L);
    assertThat(result.recommendations().get(0).reason()).isNotBlank();
  }

  @Test
  void search_语义增强只能修改理由和标签不能篡改硬字段() {
    Venue venue = venue(1L, "星辰羽毛球馆", 1, 1);
    Court court = court(101L, 1L, "普通场", "羽毛球", 8000, 1, 1);
    when(venueService.listOnline()).thenReturn(List.of(venue));
    when(courtService.listOnlineByVenue(null)).thenReturn(List.of(court));
    when(slotService.listByCourtAndDate(101L, TODAY))
        .thenReturn(List.of(slot(1L, 101L, TODAY, "18:00", 2, 8000)));
    when(semanticService.enhance(any(), eq(101L), eq("安静一点")))
        .thenReturn(
            new RecommendationEnhancement(
                Map.of(1L, "参考静音区资料"), Map.of(1L, Set.of(RecommendationTag.QUIET))));

    GlobalRecommendationItem item =
        service
            .search(new GlobalRecommendationRequest("安静一点", TODAY, null, null, null, null, 5))
            .recommendations()
            .get(0);

    assertThat(item)
        .extracting(
            GlobalRecommendationItem::venueId,
            GlobalRecommendationItem::venueName,
            GlobalRecommendationItem::venueAddress,
            GlobalRecommendationItem::courtId,
            GlobalRecommendationItem::courtName,
            GlobalRecommendationItem::courtType,
            GlobalRecommendationItem::slotId,
            GlobalRecommendationItem::date,
            GlobalRecommendationItem::startAt,
            GlobalRecommendationItem::endAt,
            GlobalRecommendationItem::price,
            GlobalRecommendationItem::available,
            GlobalRecommendationItem::score)
        .containsExactly(
            1L,
            "星辰羽毛球馆",
            "天河区体育西路 88 号",
            101L,
            "普通场",
            "羽毛球",
            1L,
            TODAY,
            LocalDateTime.of(TODAY, LocalTime.of(18, 0)),
            LocalDateTime.of(TODAY, LocalTime.of(19, 0)),
            8000,
            2,
            0L);
    assertThat(item.reason()).isEqualTo("参考静音区资料");
    assertThat(item.tags()).containsExactly(RecommendationTag.QUIET);
  }

  @Test
  void search_语义理由必须去除首尾空白并限制最大长度() {
    Venue venue = venue(1L, "星辰羽毛球馆", 1, 1);
    Court court = court(101L, 1L, "普通场", "羽毛球", 8000, 1, 1);
    String unsafeReason = "  " + "模型理由".repeat(100) + "  ";
    when(venueService.listOnline()).thenReturn(List.of(venue));
    when(courtService.listOnlineByVenue(null)).thenReturn(List.of(court));
    when(slotService.listByCourtAndDate(101L, TODAY))
        .thenReturn(List.of(slot(1L, 101L, TODAY, "18:00", 2, 8000)));
    when(semanticService.enhance(any(), eq(101L), eq("安静一点")))
        .thenReturn(new RecommendationEnhancement(Map.of(1L, unsafeReason), Map.of()));

    GlobalRecommendationItem item =
        service
            .search(new GlobalRecommendationRequest("安静一点", TODAY, null, null, null, null, 5))
            .recommendations()
            .get(0);

    assertThat(item.reason()).isNotBlank();
    assertThat(item.reason()).doesNotStartWith(" ").doesNotEndWith(" ");
    assertThat(item.reason()).hasSize(200);
  }

  private Venue venue(Long id, String name, int auditStatus, int status) {
    Venue venue = new Venue();
    venue.setId(id);
    venue.setName(name);
    venue.setAddress("天河区体育西路 88 号");
    venue.setAuditStatus(auditStatus);
    venue.setStatus(status);
    return venue;
  }

  private Court court(
      Long id, Long venueId, String name, String type, int price, int auditStatus, int status) {
    Court court = new Court();
    court.setId(id);
    court.setVenueId(venueId);
    court.setName(name);
    court.setType(type);
    court.setPrice(price);
    court.setAuditStatus(auditStatus);
    court.setStatus(status);
    return court;
  }

  private SlotVO slot(Long id, Long courtId, LocalDate date, String time, int available, int price) {
    LocalDateTime startAt = LocalDateTime.of(date, LocalTime.parse(time));
    SlotVO slot = new SlotVO();
    slot.setId(id);
    slot.setCourtId(courtId);
    slot.setBizDate(date);
    slot.setStartAt(startAt);
    slot.setEndAt(startAt.plusHours(1));
    slot.setPrice(price);
    slot.setAvailable(available);
    return slot;
  }
}
