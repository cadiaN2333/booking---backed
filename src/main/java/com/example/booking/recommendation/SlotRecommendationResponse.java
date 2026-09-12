package com.example.booking.recommendation;

import java.util.List;

/** 规则推荐响应；当前版本始终为降级模式。 */
public record SlotRecommendationResponse(
    List<SlotRecommendationItem> recommendations, boolean degraded) {}
