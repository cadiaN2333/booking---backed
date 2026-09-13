package com.example.booking.recommendation;

import com.example.booking.domain.vo.SlotVO;
import com.example.booking.service.SlotService;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 基于当前场地当前日期时段快照的只读规则推荐服务。 */
@Service
public class SlotRecommendationService {

  private static final String DEFAULT_REASON = "按时间与可约余量为你排序";
  private static final long EVENING_SCORE = 1000L;
  private static final LocalTime EVENING_START = LocalTime.of(18, 0);
  private static final LocalTime EVENING_END = LocalTime.of(22, 0);

  private final SlotService slotService;
  private final RecommendationSemanticService semanticService;

  @Autowired
  public SlotRecommendationService(
      SlotService slotService, RecommendationSemanticService semanticService) {
    this.slotService = slotService;
    this.semanticService = semanticService;
  }

  public SlotRecommendationService(SlotService slotService) {
    this(slotService, RecommendationSemanticService.disabled());
  }

  public SlotRecommendationResponse recommend(SlotRecommendationRequest request) {
    Set<RecommendationTag> tags = request.resolvedTags();
    List<SlotVO> candidates =
        slotService.listByCourtAndDate(request.courtId(), request.date()).stream()
            .filter(slot -> belongsToRequest(slot, request))
            .filter(slot -> slot.getAvailable() != null && slot.getAvailable() > 0)
            .toList();
    SlotVO earliest =
        candidates.stream()
            .min(Comparator.comparing(SlotVO::getStartAt).thenComparing(SlotVO::getId))
            .orElse(null);
    int maxAvailable = candidates.stream().mapToInt(SlotVO::getAvailable).max().orElse(0);
    List<SlotRecommendationItem> rankedRecommendations =
        candidates.stream()
            .map(slot -> toItem(slot, tags, earliest, maxAvailable))
            .sorted(
                Comparator.comparingLong(SlotRecommendationItem::score)
                    .reversed()
                    .thenComparing(SlotRecommendationItem::startAt)
                    .thenComparing(SlotRecommendationItem::slotId))
            .toList();
    List<SlotRecommendationItem> recommendations =
        rankedRecommendations.stream().limit(request.resolvedLimit()).toList();
    return enhance(recommendations, request);
  }

  private SlotRecommendationResponse enhance(
      List<SlotRecommendationItem> recommendations, SlotRecommendationRequest request) {
    if (recommendations.isEmpty()) {
      return new SlotRecommendationResponse(recommendations, true);
    }
    try {
      RecommendationEnhancement enhancement =
          semanticService.enhance(recommendations, request.courtId(), request.query());
      if (enhancement == null) {
        return new SlotRecommendationResponse(recommendations, true);
      }
      RecommendationEnhancement allowedEnhancement =
          enhancement.onlyFor(
              recommendations.stream().map(SlotRecommendationItem::slotId).toList());
      if (allowedEnhancement.isEmpty()) {
        return new SlotRecommendationResponse(recommendations, true);
      }
      List<SlotRecommendationItem> enhancedRecommendations =
          recommendations.stream().map(item -> applyEnhancement(item, allowedEnhancement)).toList();
      return new SlotRecommendationResponse(enhancedRecommendations, false);
    } catch (RuntimeException ignored) {
      return new SlotRecommendationResponse(recommendations, true);
    }
  }

  private SlotRecommendationItem applyEnhancement(
      SlotRecommendationItem item, RecommendationEnhancement enhancement) {
    Set<RecommendationTag> mergedTags =
        Stream.concat(item.tags().stream(), enhancement.tagsFor(item.slotId()).stream())
            .collect(Collectors.toUnmodifiableSet());
    return new SlotRecommendationItem(
        item.slotId(),
        item.courtId(),
        item.startAt(),
        item.endAt(),
        item.price(),
        item.available(),
        item.score(),
        mergedTags,
        enhancement.reasonFor(item.slotId()).orElse(item.reason()));
  }

  private boolean belongsToRequest(SlotVO slot, SlotRecommendationRequest request) {
    return Objects.equals(slot.getCourtId(), request.courtId())
        && Objects.equals(slot.getBizDate(), request.date())
        && slot.getStartAt() != null
        && slot.getId() != null;
  }

  private SlotRecommendationItem toItem(
      SlotVO slot, Set<RecommendationTag> tags, SlotVO earliest, int maxAvailable) {
    return new SlotRecommendationItem(
        slot.getId(),
        slot.getCourtId(),
        slot.getStartAt(),
        slot.getEndAt(),
        slot.getPrice(),
        slot.getAvailable(),
        score(slot, tags),
        matchedTags(slot, tags, earliest, maxAvailable),
        DEFAULT_REASON);
  }

  private Set<RecommendationTag> matchedTags(
      SlotVO slot, Set<RecommendationTag> requestedTags, SlotVO earliest, int maxAvailable) {
    EnumSet<RecommendationTag> matched = EnumSet.noneOf(RecommendationTag.class);
    LocalTime startTime = slot.getStartAt().toLocalTime();
    if (requestedTags.contains(RecommendationTag.EVENING)
        && !startTime.isBefore(EVENING_START)
        && startTime.isBefore(EVENING_END)) {
      matched.add(RecommendationTag.EVENING);
    }
    if (requestedTags.contains(RecommendationTag.EARLIEST)
        && earliest != null
        && Objects.equals(slot.getId(), earliest.getId())) {
      matched.add(RecommendationTag.EARLIEST);
    }
    if (requestedTags.contains(RecommendationTag.CAPACITY)
        && slot.getAvailable() == maxAvailable) {
      matched.add(RecommendationTag.CAPACITY);
    }
    return Set.copyOf(matched);
  }

  private long score(SlotVO slot, Set<RecommendationTag> tags) {
    long score = 0L;
    LocalTime startTime = slot.getStartAt().toLocalTime();
    if (tags.contains(RecommendationTag.EVENING)
        && !startTime.isBefore(EVENING_START)
        && startTime.isBefore(EVENING_END)) {
      score += EVENING_SCORE;
    }
    if (tags.contains(RecommendationTag.EARLIEST)) {
      score += 24 * 60 - startTime.toSecondOfDay() / 60;
    }
    if (tags.contains(RecommendationTag.CAPACITY)) {
      score += slot.getAvailable();
    }
    return score;
  }
}
