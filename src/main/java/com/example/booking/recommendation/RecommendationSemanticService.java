package com.example.booking.recommendation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 受约束的公开场地语义检索服务，不负责生成或改变推荐候选。 */
@Service
public class RecommendationSemanticService {

  private static final String DEFAULT_QUERY = "场地设施与预约规则";

  private final ObjectProvider<VectorStore> vectorStoreProvider;
  private final boolean vectorEnabled;
  private final boolean chatEnabled;

  public RecommendationSemanticService(
      ObjectProvider<VectorStore> vectorStoreProvider,
      @Value("${booking.recommendation.vector-enabled:false}") boolean vectorEnabled,
      @Value("${booking.recommendation.chat-enabled:false}") boolean chatEnabled) {
    this.vectorStoreProvider = vectorStoreProvider;
    this.vectorEnabled = vectorEnabled;
    this.chatEnabled = chatEnabled;
  }

  static RecommendationSemanticService disabled() {
    return new RecommendationSemanticService(null, false, false);
  }

  public RecommendationEnhancement enhance(
      List<SlotRecommendationItem> sortedCandidates, Long courtId, String query) {
    if (!vectorEnabled) {
      return RecommendationEnhancement.empty();
    }
    if (chatEnabled) {
      throw new IllegalStateException("ChatClient 未配置，无法生成语义理由");
    }
    if (sortedCandidates == null || sortedCandidates.isEmpty() || courtId == null) {
      return RecommendationEnhancement.empty();
    }

    VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
    if (vectorStore == null) {
      throw new IllegalStateException("推荐向量库不可用");
    }

    String resolvedQuery = query == null || query.isBlank() ? DEFAULT_QUERY : query.trim();
    SearchRequest searchRequest =
        SearchRequest.builder()
            .query(resolvedQuery)
            .topK(1)
            .similarityThresholdAll()
            .filterExpression("courtId == '" + courtId + "'")
            .build();
    List<Document> documents = vectorStore.similaritySearch(searchRequest);
    if (documents == null
        || documents.stream().noneMatch(document -> isPublicCourtDocument(document, courtId))) {
      return RecommendationEnhancement.empty();
    }

    String reason = "结合当前场地公开场地资料与你的偏好推荐";
    Map<Long, String> reasons = new LinkedHashMap<>();
    for (SlotRecommendationItem candidate : sortedCandidates) {
      if (candidate != null
          && Objects.equals(candidate.courtId(), courtId)
          && candidate.slotId() != null) {
        reasons.put(candidate.slotId(), reason);
      }
    }
    return new RecommendationEnhancement(reasons, Map.of());
  }

  private boolean isPublicCourtDocument(Document document, Long courtId) {
    if (document == null || document.getMetadata() == null) {
      return false;
    }
    Map<String, Object> metadata = document.getMetadata();
    Object status = metadata.get("status");
    Object documentType = metadata.get("documentType");
    return Objects.equals(String.valueOf(courtId), String.valueOf(metadata.get("courtId")))
        && (status == null || Objects.equals("1", String.valueOf(status)))
        && (documentType == null || Objects.equals("court_profile", documentType));
  }
}
