package com.example.booking.recommendation;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.ai.document.Document;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 受约束的公开场地语义检索服务，不负责生成或改变推荐候选。 */
@Service
public class RecommendationSemanticService {

  private static final String DEFAULT_QUERY = "场地设施与预约规则";
  private static final String PUBLIC_DOCUMENT_FILTER =
      "courtId == '%s' && status == '1' && documentType == 'court_profile'";

  private final ObjectProvider<VectorStore> vectorStoreProvider;
  private final ObjectProvider<ChatModel> chatModelProvider;
  private final boolean vectorEnabled;
  private final boolean chatEnabled;

  public RecommendationSemanticService(
      ObjectProvider<VectorStore> vectorStoreProvider,
      @Value("${booking.recommendation.vector-enabled:false}") boolean vectorEnabled,
      @Value("${booking.recommendation.chat-enabled:false}") boolean chatEnabled) {
    this(vectorStoreProvider, null, vectorEnabled, chatEnabled);
  }

  @Autowired
  public RecommendationSemanticService(
      ObjectProvider<VectorStore> vectorStoreProvider,
      ObjectProvider<ChatModel> chatModelProvider,
      @Value("${booking.recommendation.vector-enabled:false}") boolean vectorEnabled,
      @Value("${booking.recommendation.chat-enabled:false}") boolean chatEnabled) {
    this.vectorStoreProvider = vectorStoreProvider;
    this.chatModelProvider = chatModelProvider;
    this.vectorEnabled = vectorEnabled;
    this.chatEnabled = chatEnabled;
  }

  static RecommendationSemanticService disabled() {
    return new RecommendationSemanticService(null, false, false);
  }

  public RecommendationEnhancement enhance(
      List<SlotRecommendationItem> sortedCandidates, Long courtId, String query) {
    if (sortedCandidates == null || sortedCandidates.isEmpty() || courtId == null) {
      return RecommendationEnhancement.empty();
    }

    List<Document> documents = vectorEnabled ? retrievePublicDocuments(courtId, query) : List.of();
    if (chatEnabled) {
      return enhanceWithChat(sortedCandidates, query, documents);
    }
    return enhanceWithDocumentContent(sortedCandidates, courtId, documents);
  }

  private List<Document> retrievePublicDocuments(Long courtId, String query) {
    VectorStore vectorStore = vectorStoreProvider == null ? null : vectorStoreProvider.getIfAvailable();
    if (vectorStore == null) {
      throw new IllegalStateException("推荐向量库不可用");
    }

    String resolvedQuery = query == null || query.isBlank() ? DEFAULT_QUERY : query.trim();
    SearchRequest searchRequest =
        SearchRequest.builder()
            .query(resolvedQuery)
            .topK(1)
            .similarityThresholdAll()
            .filterExpression(PUBLIC_DOCUMENT_FILTER.formatted(courtId))
            .build();
    List<Document> documents = vectorStore.similaritySearch(searchRequest);
    if (documents == null) {
      return List.of();
    }
    return documents.stream()
        .filter(document -> isPublicCourtDocument(document, courtId))
        .toList();
  }

  private RecommendationEnhancement enhanceWithDocumentContent(
      List<SlotRecommendationItem> sortedCandidates,
      Long courtId,
      List<Document> documents) {
    String content = firstDocumentContent(documents);
    if (content == null) {
      return RecommendationEnhancement.empty();
    }

    String reason = "参考公开场地资料：" + compact(content);
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

  private RecommendationEnhancement enhanceWithChat(
      List<SlotRecommendationItem> sortedCandidates,
      String query,
      List<Document> documents) {
    try {
      ChatModel chatModel = chatModelProvider == null ? null : chatModelProvider.getIfAvailable();
      if (chatModel == null) {
        return RecommendationEnhancement.empty();
      }
      ChatResponse response = chatModel.call(new Prompt(buildPrompt(sortedCandidates, query, documents)));
      return parseChatResponse(response, sortedCandidates);
    } catch (RuntimeException ignored) {
      return RecommendationEnhancement.empty();
    }
  }

  private String buildPrompt(
      List<SlotRecommendationItem> sortedCandidates,
      String query,
      List<Document> documents) {
    StringBuilder prompt =
        new StringBuilder(
            "你是预约推荐理由助手。只能为输入候选生成理由，不能新增或重排候选。"
                + "每行严格输出 slotId|理由|标签，标签只能使用 EVENING、EARLIEST、CAPACITY、QUIET；"
                + "没有标签时省略第三段。禁止输出其它内容。\n")
            .append("用户偏好：")
            .append(query == null || query.isBlank() ? "无" : query.trim())
            .append("\n候选（已按规则排序）：\n");
    sortedCandidates.forEach(
        candidate ->
            prompt
                .append("slotId=")
                .append(candidate.slotId())
                .append(", courtId=")
                .append(candidate.courtId())
                .append(", startAt=")
                .append(candidate.startAt())
                .append(", endAt=")
                .append(candidate.endAt())
                .append(", price=")
                .append(candidate.price())
                .append(", available=")
                .append(candidate.available())
                .append(", score=")
                .append(candidate.score())
                .append(", tags=")
                .append(candidate.tags())
                .append('\n'));
    prompt.append("公开场地文档内容：\n");
    String content = firstDocumentContent(documents);
    prompt.append(content == null ? "无（向量检索关闭或没有有效公开文档）" : content);
    return prompt.toString();
  }

  private RecommendationEnhancement parseChatResponse(
      ChatResponse response, List<SlotRecommendationItem> sortedCandidates) {
    if (response == null) {
      return RecommendationEnhancement.empty();
    }
    Generation generation = response.getResult();
    AssistantMessage output = generation == null ? null : generation.getOutput();
    if (output == null || output.getText() == null || output.getText().isBlank()) {
      return RecommendationEnhancement.empty();
    }

    Set<Long> candidateIds =
        sortedCandidates.stream().map(SlotRecommendationItem::slotId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
    Map<Long, String> reasons = new LinkedHashMap<>();
    Map<Long, Set<RecommendationTag>> tags = new LinkedHashMap<>();
    for (String line : output.getText().lines().map(String::trim).filter(line -> !line.isBlank()).toList()) {
      String[] parts = line.split("\\|", -1);
      if (parts.length < 2 || parts.length > 3) {
        return RecommendationEnhancement.empty();
      }
      Long slotId;
      try {
        slotId = Long.valueOf(parts[0].trim());
      } catch (NumberFormatException ignored) {
        return RecommendationEnhancement.empty();
      }
      String reason = parts[1].trim();
      if (reason.isBlank() || reasons.containsKey(slotId)) {
        return RecommendationEnhancement.empty();
      }
      if (!candidateIds.contains(slotId)) {
        continue;
      }
      reasons.put(slotId, reason);
      if (parts.length == 3 && !parts[2].isBlank()) {
        EnumSet<RecommendationTag> parsedTags = EnumSet.noneOf(RecommendationTag.class);
        for (String tag : parts[2].split(",")) {
          try {
            parsedTags.add(RecommendationTag.valueOf(tag.trim()));
          } catch (IllegalArgumentException ignored) {
            return RecommendationEnhancement.empty();
          }
        }
        if (!parsedTags.isEmpty()) {
          tags.put(slotId, parsedTags);
        }
      }
    }
    return new RecommendationEnhancement(reasons, tags).onlyFor(candidateIds);
  }

  private String firstDocumentContent(List<Document> documents) {
    if (documents == null) {
      return null;
    }
    return documents.stream()
        .map(Document::getText)
        .filter(text -> text != null && !text.isBlank())
        .findFirst()
        .orElse(null);
  }

  private String compact(String content) {
    String normalized = content.replaceAll("\\s+", " ").trim();
    return normalized.length() <= 160 ? normalized : normalized.substring(0, 160) + "…";
  }

  private boolean isPublicCourtDocument(Document document, Long courtId) {
    if (document == null || document.getMetadata() == null) {
      return false;
    }
    Map<String, Object> metadata = document.getMetadata();
    return Objects.equals(String.valueOf(courtId), String.valueOf(metadata.get("courtId")))
        && Objects.equals("1", String.valueOf(metadata.get("status")))
        && Objects.equals("court_profile", metadata.get("documentType"));
  }
}
