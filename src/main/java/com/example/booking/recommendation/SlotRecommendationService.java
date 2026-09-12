package com.example.booking.recommendation;

import com.example.booking.domain.vo.SlotVO;
import com.example.booking.service.SlotService;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 基于当前场地当前日期时段快照的只读规则推荐服务。 */
@Service
@RequiredArgsConstructor
public class SlotRecommendationService {

  private static final String DEFAULT_REASON = "按时间与可约余量为你排序";
  private static final int EVENING_SCORE = 1000;
  private static final LocalTime EVENING_START = LocalTime.of(18, 0);
  private static final LocalTime EVENING_END = LocalTime.of(22, 0);

  private final SlotService slotService;

  public SlotRecommendationResponse recommend(SlotRecommendationRequest request) {
    Set<RecommendationTag> tags = request.resolvedTags();
    List<SlotRecommendationItem> recommendations =
        slotService.listByCourtAndDate(request.courtId(), request.date()).stream()
            .filter(slot -> belongsToRequest(slot, request))
            .filter(slot -> slot.getAvailable() != null && slot.getAvailable() > 0)
            .map(slot -> toItem(slot, tags))
            .sorted(
                Comparator.comparingInt(SlotRecommendationItem::score)
                    .reversed()
                    .thenComparing(SlotRecommendationItem::startAt)
                    .thenComparing(SlotRecommendationItem::slotId))
            .limit(request.resolvedLimit())
            .toList();
    return new SlotRecommendationResponse(recommendations, true);
  }

  private boolean belongsToRequest(SlotVO slot, SlotRecommendationRequest request) {
    return Objects.equals(slot.getCourtId(), request.courtId())
        && Objects.equals(slot.getBizDate(), request.date())
        && slot.getStartAt() != null
        && slot.getId() != null;
  }

  private SlotRecommendationItem toItem(SlotVO slot, Set<RecommendationTag> tags) {
    return new SlotRecommendationItem(
        slot.getId(),
        slot.getCourtId(),
        slot.getStartAt(),
        slot.getEndAt(),
        slot.getPrice(),
        slot.getAvailable(),
        score(slot, tags),
        tags,
        DEFAULT_REASON);
  }

  private int score(SlotVO slot, Set<RecommendationTag> tags) {
    int score = slot.getAvailable();
    LocalTime startTime = slot.getStartAt().toLocalTime();
    if (tags.contains(RecommendationTag.EVENING)
        && !startTime.isBefore(EVENING_START)
        && startTime.isBefore(EVENING_END)) {
      score += EVENING_SCORE;
    }
    if (tags.contains(RecommendationTag.EARLIEST)) {
      score += 24 * 60 - startTime.toSecondOfDay() / 60;
    }
    return score;
  }
}
