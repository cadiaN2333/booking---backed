package com.example.booking.recommendation;

import java.util.List;

/** 规则与语义增强推荐响应；语义能力不可用时 degraded 为 true。 */
public record SlotRecommendationResponse(
    List<SlotRecommendationItem> recommendations, boolean degraded) {}
