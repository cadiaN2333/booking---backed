package com.example.booking.recommendation;

import com.example.booking.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 顾客端当前日期时段推荐入口，只提供只读候选。 */
@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

  private final SlotRecommendationService recommendationService;

  @PostMapping("/slots")
  public Result<SlotRecommendationResponse> recommend(
      @Valid @RequestBody SlotRecommendationRequest request) {
    return Result.ok(recommendationService.recommend(request));
  }
}
