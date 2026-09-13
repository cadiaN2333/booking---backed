package com.example.booking.recommendation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/** 全局推荐中的单个场馆、场地和可预约时段快照。 */
public record GlobalRecommendationItem(
    Long venueId,
    String venueName,
    String venueAddress,
    Long courtId,
    String courtName,
    String courtType,
    Long slotId,
    LocalDate date,
    LocalDateTime startAt,
    LocalDateTime endAt,
    Integer price,
    Integer available,
    long score,
    Set<RecommendationTag> tags,
    String reason) {

  public GlobalRecommendationItem {
    tags = tags == null ? Set.of() : Set.copyOf(tags);
    reason = reason == null ? "" : reason;
  }
}
