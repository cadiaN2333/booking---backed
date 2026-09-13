package com.example.booking.recommendation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 只允许作用于规则候选的语义增强结果。 */
public record RecommendationEnhancement(
    Map<Long, String> reasons, Map<Long, Set<RecommendationTag>> tags) {

  public RecommendationEnhancement {
    reasons = normalizeReasons(reasons);
    tags = normalizeTags(tags);
  }

  public static RecommendationEnhancement empty() {
    return new RecommendationEnhancement(Map.of(), Map.of());
  }

  public Optional<String> reasonFor(Long slotId) {
    return Optional.ofNullable(reasons.get(slotId));
  }

  public Set<RecommendationTag> tagsFor(Long slotId) {
    return tags.getOrDefault(slotId, Set.of());
  }

  public boolean isEmpty() {
    return reasons.isEmpty() && tags.isEmpty();
  }

  /** 按当前规则候选白名单裁剪模型或外部服务返回的结果。 */
  public RecommendationEnhancement onlyFor(Collection<Long> candidateIds) {
    Set<Long> allowedIds = Set.copyOf(candidateIds);
    Map<Long, String> allowedReasons =
        reasons.entrySet().stream()
            .filter(entry -> allowedIds.contains(entry.getKey()))
            .collect(
                LinkedHashMap::new,
                (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                Map::putAll);
    Map<Long, Set<RecommendationTag>> allowedTags =
        tags.entrySet().stream()
            .filter(entry -> allowedIds.contains(entry.getKey()))
            .collect(
                LinkedHashMap::new,
                (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                Map::putAll);
    return new RecommendationEnhancement(allowedReasons, allowedTags);
  }

  private static Map<Long, String> normalizeReasons(Map<Long, String> input) {
    if (input == null || input.isEmpty()) {
      return Map.of();
    }
    Map<Long, String> normalized = new LinkedHashMap<>();
    input.forEach(
        (slotId, reason) -> {
          if (slotId != null && reason != null && !reason.isBlank()) {
            normalized.put(slotId, reason.trim());
          }
        });
    return Map.copyOf(normalized);
  }

  private static Map<Long, Set<RecommendationTag>> normalizeTags(
      Map<Long, Set<RecommendationTag>> input) {
    if (input == null || input.isEmpty()) {
      return Map.of();
    }
    Map<Long, Set<RecommendationTag>> normalized = new LinkedHashMap<>();
    input.forEach(
        (slotId, values) -> {
          if (slotId != null && values != null && !values.isEmpty()) {
            Set<RecommendationTag> nonNullValues =
                values.stream()
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet());
            if (!nonNullValues.isEmpty()) {
              normalized.put(slotId, nonNullValues);
            }
          }
        });
    return Map.copyOf(normalized);
  }
}
