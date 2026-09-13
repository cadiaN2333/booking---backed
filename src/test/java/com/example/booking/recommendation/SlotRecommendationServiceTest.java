package com.example.booking.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.booking.domain.vo.SlotVO;
import com.example.booking.service.SlotService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SlotRecommendationServiceTest {

  private static final LocalDate DATE = LocalDate.of(2026, 9, 13);

  private SlotService slotService;
  private RecommendationSemanticService semanticService;
  private SlotRecommendationService service;

  @BeforeEach
  void setUp() {
    slotService = mock(SlotService.class);
    semanticService = mock(RecommendationSemanticService.class);
    service = new SlotRecommendationService(slotService, semanticService);
  }

  @Test
  void recommend_只返回当前场地日期的可约时段并优先傍晚() {
    when(slotService.listByCourtAndDate(101L, DATE))
        .thenReturn(
            List.of(
                slot(1L, 101L, DATE, "10:00", 3),
                slot(2L, 101L, DATE, "18:00", 1),
                slot(3L, 101L, DATE, "20:00", 2),
                slot(4L, 101L, DATE, "21:00", 0),
                slot(5L, 102L, DATE, "19:00", 8),
                slot(6L, 101L, DATE.plusDays(1), "19:00", 8)));

    SlotRecommendationResponse result =
        service.recommend(request(101L, DATE, Set.of(RecommendationTag.EVENING), 3));

    assertThat(result.recommendations())
        .extracting(SlotRecommendationItem::slotId)
        .containsExactly(2L, 3L, 1L);
    assertThat(result.recommendations()).allMatch(item -> item.available() > 0);
    verify(slotService).listByCourtAndDate(101L, DATE);
  }

  @Test
  void recommend_未选择容量时较早时段不会因余量较少落后() {
    when(slotService.listByCourtAndDate(101L, DATE))
        .thenReturn(
            List.of(
                slot(1L, 101L, DATE, "18:00", 1),
                slot(2L, 101L, DATE, "19:00", 9)));

    SlotRecommendationResponse result =
        service.recommend(request(101L, DATE, Set.of(RecommendationTag.EVENING), 2));

    assertThat(result.recommendations())
        .extracting(SlotRecommendationItem::slotId)
        .containsExactly(1L, 2L);
  }

  @Test
  void recommend_按偏好评分并以开始时间和时段编号稳定排序() {
    when(slotService.listByCourtAndDate(101L, DATE))
        .thenReturn(
            List.of(
                slot(3L, 101L, DATE, "09:00", 1),
                slot(2L, 101L, DATE, "09:00", 1),
                slot(4L, 101L, DATE, "10:00", 6)));

    SlotRecommendationResponse earliest =
        service.recommend(request(101L, DATE, Set.of(RecommendationTag.EARLIEST), 3));
    SlotRecommendationResponse capacity =
        service.recommend(request(101L, DATE, Set.of(RecommendationTag.CAPACITY), 3));

    assertThat(earliest.recommendations())
        .extracting(SlotRecommendationItem::slotId)
        .containsExactly(2L, 3L, 4L);
    assertThat(capacity.recommendations())
        .extracting(SlotRecommendationItem::slotId)
        .containsExactly(4L, 2L, 3L);
  }

  @Test
  void recommend_使用默认上限固定理由并标记规则降级() {
    when(slotService.listByCourtAndDate(101L, DATE))
        .thenReturn(
            List.of(
                slot(1L, 101L, DATE, "09:00", 1),
                slot(2L, 101L, DATE, "10:00", 2),
                slot(3L, 101L, DATE, "11:00", 3),
                slot(4L, 101L, DATE, "12:00", 4)));

    SlotRecommendationResponse quiet =
        service.recommend(request(101L, DATE, Set.of(RecommendationTag.QUIET), null));
    SlotRecommendationResponse noTag = service.recommend(request(101L, DATE, Set.of(), null));

    assertThat(quiet.recommendations()).hasSize(3);
    assertThat(quiet.degraded()).isTrue();
    assertThat(quiet.recommendations())
        .allSatisfy(
            item -> {
              assertThat(item.reason()).isEqualTo("按时间与可约余量为你排序");
              assertThat(item.tags()).isEmpty();
            });
    assertThat(quiet.recommendations()).extracting(SlotRecommendationItem::score)
        .containsExactlyElementsOf(noTag.recommendations().stream().map(SlotRecommendationItem::score).toList());
  }

  @Test
  void recommend_傍晚标签只标记傍晚候选() {
    when(slotService.listByCourtAndDate(101L, DATE))
        .thenReturn(
            List.of(
                slot(1L, 101L, DATE, "10:00", 2),
                slot(2L, 101L, DATE, "18:00", 2)));

    SlotRecommendationResponse result =
        service.recommend(request(101L, DATE, Set.of(RecommendationTag.EVENING), 2));

    assertThat(itemById(result, 1L).tags()).doesNotContain(RecommendationTag.EVENING);
    assertThat(itemById(result, 2L).tags()).containsExactly(RecommendationTag.EVENING);
  }

  @Test
  void recommend_容量标签只标记余量最高候选() {
    when(slotService.listByCourtAndDate(101L, DATE))
        .thenReturn(
            List.of(
                slot(1L, 101L, DATE, "09:00", 1),
                slot(2L, 101L, DATE, "10:00", 3),
                slot(3L, 101L, DATE, "11:00", 2)));

    SlotRecommendationResponse result =
        service.recommend(request(101L, DATE, Set.of(RecommendationTag.CAPACITY), 3));

    assertThat(itemById(result, 1L).tags()).doesNotContain(RecommendationTag.CAPACITY);
    assertThat(itemById(result, 2L).tags()).containsExactly(RecommendationTag.CAPACITY);
    assertThat(itemById(result, 3L).tags()).doesNotContain(RecommendationTag.CAPACITY);
  }

  @Test
  void recommend_尽早标签只标记最早候选() {
    when(slotService.listByCourtAndDate(101L, DATE))
        .thenReturn(
            List.of(
                slot(1L, 101L, DATE, "09:00", 2),
                slot(2L, 101L, DATE, "10:00", 2)));

    SlotRecommendationResponse result =
        service.recommend(request(101L, DATE, Set.of(RecommendationTag.EARLIEST), 2));

    assertThat(itemById(result, 1L).tags()).containsExactly(RecommendationTag.EARLIEST);
    assertThat(itemById(result, 2L).tags()).doesNotContain(RecommendationTag.EARLIEST);
  }

  @Test
  void recommend_降级模式不返回安静标签() {
    when(slotService.listByCourtAndDate(101L, DATE))
        .thenReturn(List.of(slot(1L, 101L, DATE, "09:00", 2)));

    SlotRecommendationResponse result =
        service.recommend(request(101L, DATE, Set.of(RecommendationTag.QUIET), 1));

    assertThat(result.degraded()).isTrue();
    assertThat(itemById(result, 1L).tags()).doesNotContain(RecommendationTag.QUIET);
  }

  @Test
  void recommend_超大余量评分不溢出且容量排序正确() {
    when(slotService.listByCourtAndDate(101L, DATE))
        .thenReturn(
            List.of(
                slot(1L, 101L, DATE, "18:00", Integer.MAX_VALUE),
                slot(2L, 101L, DATE, "19:00", 1)));

    SlotRecommendationResponse result =
        service.recommend(
            request(
                101L,
                DATE,
                Set.of(RecommendationTag.EVENING, RecommendationTag.CAPACITY),
                2));

    assertThat(result.recommendations()).extracting(SlotRecommendationItem::slotId)
        .containsExactly(1L, 2L);
    assertThat(result.recommendations().get(0).score())
        .isEqualTo((long) Integer.MAX_VALUE + 1000L);
  }

  @Test
  void recommend_语义服务成功时只增强已排序候选且不改变硬字段() {
    when(slotService.listByCourtAndDate(101L, DATE))
        .thenReturn(List.of(slot(1L, 101L, DATE, "18:00", 2)));
    when(
            semanticService.enhance(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.isNull()))
        .thenReturn(
            new RecommendationEnhancement(
                java.util.Map.of(1L, "结合公开场地资料推荐"), java.util.Map.of()));

    SlotRecommendationResponse result =
        service.recommend(request(101L, DATE, Set.of(), 1));

    assertThat(result.degraded()).isFalse();
    assertThat(result.recommendations().get(0))
        .extracting(SlotRecommendationItem::slotId, SlotRecommendationItem::courtId,
            SlotRecommendationItem::startAt, SlotRecommendationItem::endAt,
            SlotRecommendationItem::price, SlotRecommendationItem::available)
        .containsExactly(1L, 101L, LocalDateTime.of(DATE, java.time.LocalTime.of(18, 0)),
            LocalDateTime.of(DATE, java.time.LocalTime.of(19, 0)), 8000, 2);
    assertThat(result.recommendations().get(0).reason()).isEqualTo("结合公开场地资料推荐");
  }

  @Test
  void recommend_语义服务返回未知时段时丢弃未知ID并保留规则结果() {
    when(slotService.listByCourtAndDate(101L, DATE))
        .thenReturn(List.of(slot(1L, 101L, DATE, "18:00", 2)));
    when(
            semanticService.enhance(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.isNull()))
        .thenReturn(
            new RecommendationEnhancement(java.util.Map.of(999L, "伪造理由"), java.util.Map.of()));

    SlotRecommendationResponse result =
        service.recommend(request(101L, DATE, Set.of(), 1));

    assertThat(result.degraded()).isTrue();
    assertThat(result.recommendations()).extracting(SlotRecommendationItem::slotId).containsExactly(1L);
    assertThat(result.recommendations().get(0).reason()).isEqualTo("按时间与可约余量为你排序");
  }

  @Test
  void recommend_语义服务异常时保留规则候选并标记降级() {
    when(slotService.listByCourtAndDate(101L, DATE))
        .thenReturn(List.of(slot(1L, 101L, DATE, "18:00", 2)));
    when(
            semanticService.enhance(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.isNull()))
        .thenThrow(new IllegalStateException("vector down"));

    SlotRecommendationResponse result =
        service.recommend(request(101L, DATE, Set.of(), 1));

    assertThat(result.degraded()).isTrue();
    assertThat(result.recommendations()).extracting(SlotRecommendationItem::slotId).containsExactly(1L);
    assertThat(result.recommendations().get(0).reason()).isEqualTo("按时间与可约余量为你排序");
  }

  private SlotRecommendationItem itemById(SlotRecommendationResponse response, Long slotId) {
    return response.recommendations().stream()
        .filter(item -> item.slotId().equals(slotId))
        .findFirst()
        .orElseThrow();
  }

  private SlotRecommendationRequest request(
      Long courtId, LocalDate date, Set<RecommendationTag> tags, Integer limit) {
    return new SlotRecommendationRequest(courtId, date, null, tags, limit);
  }

  private SlotVO slot(Long id, Long courtId, LocalDate date, String time, int available) {
    LocalDateTime startAt = LocalDateTime.of(date, java.time.LocalTime.parse(time));
    SlotVO slot = new SlotVO();
    slot.setId(id);
    slot.setCourtId(courtId);
    slot.setBizDate(date);
    slot.setStartAt(startAt);
    slot.setEndAt(startAt.plusHours(1));
    slot.setPrice(8000);
    slot.setAvailable(available);
    return slot;
  }
}
