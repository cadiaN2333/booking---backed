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
  private SlotRecommendationService service;

  @BeforeEach
  void setUp() {
    slotService = mock(SlotService.class);
    service = new SlotRecommendationService(slotService);
  }

  @Test
  void recommend_只返回当前场地日期的可约时段并优先傍晚余量() {
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
        .containsExactly(3L, 2L, 1L);
    assertThat(result.recommendations()).allMatch(item -> item.available() > 0);
    verify(slotService).listByCourtAndDate(101L, DATE);
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
              assertThat(item.tags()).containsExactly(RecommendationTag.QUIET);
            });
    assertThat(quiet.recommendations()).extracting(SlotRecommendationItem::score)
        .containsExactlyElementsOf(noTag.recommendations().stream().map(SlotRecommendationItem::score).toList());
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
