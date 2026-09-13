package com.example.booking.recommendation;

import java.time.LocalDateTime;
import java.util.Set;

/** 单个可预约时段的规则推荐快照。 */
public record SlotRecommendationItem(
    Long slotId,
    Long courtId,
    LocalDateTime startAt,
    LocalDateTime endAt,
    Integer price,
    Integer available,
    long score,
    Set<RecommendationTag> tags,
    String reason) {}
