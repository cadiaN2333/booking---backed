package com.example.booking.recommendation;

import com.example.booking.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 顾客端跨场馆推荐入口。 */
@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
public class GlobalRecommendationController {

  private final GlobalRecommendationService recommendationService;

  @PostMapping("/search")
  public Result<GlobalRecommendationResponse> search(
      @Valid @RequestBody GlobalRecommendationRequest request) {
    return Result.ok(recommendationService.search(request));
  }
}
