package com.example.booking.recommendation;

import java.util.List;

/** 全局推荐响应；degraded 为 true 时表示未使用或未成功使用语义增强。 */
public record GlobalRecommendationResponse(
    List<GlobalRecommendationItem> recommendations, boolean degraded) {

  public GlobalRecommendationResponse {
    recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
  }
}
