package com.example.booking.recommendation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;

/** 当前场地、当前日期的只读时段推荐请求。 */
public record SlotRecommendationRequest(
    @NotNull(message = "courtId 必填") @Positive(message = "courtId 必须为正整数") Long courtId,
    @NotNull(message = "date 必填") LocalDate date,
    @Size(max = 300, message = "query 最多 300 个字符") String query,
    @Size(max = 4, message = "tags 最多 4 个") Set<@NotNull(message = "参数不合法") RecommendationTag> tags,
    @Min(value = 1, message = "limit 最小为 1") @Max(value = 3, message = "limit 最大为 3") Integer limit) {

  public int resolvedLimit() {
    return limit == null ? 3 : limit;
  }

  public Set<RecommendationTag> resolvedTags() {
    return tags == null ? Set.of() : Set.copyOf(tags);
  }
}
