package com.example.booking.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

class RecommendationSemanticServiceTest {

  private VectorStore vectorStore;
  private ObjectProvider<VectorStore> vectorStoreProvider;
  private RecommendationSemanticService service;

  @BeforeEach
  void setUp() {
    vectorStore = mock(VectorStore.class);
    vectorStoreProvider = mock(ObjectProvider.class);
    when(vectorStoreProvider.getIfAvailable()).thenReturn(vectorStore);
    service = new RecommendationSemanticService(vectorStoreProvider, true, false);
  }

  @Test
  void enhance_按当前场地检索公开文档并返回语义理由() {
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(
            List.of(
                new Document(
                    "doc-1",
                    "安静区、淋浴、更衣室",
                    Map.of("courtId", "101", "status", "1", "documentType", "court_profile"))));

    RecommendationEnhancement result =
        service.enhance(List.of(candidate(1L)), 101L, "想要安静一些");

    assertThat(result.reasonFor(1L))
        .hasValueSatisfying(reason -> assertThat(reason).contains("公开场地资料"));
    ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    org.mockito.Mockito.verify(vectorStore).similaritySearch(captor.capture());
    assertThat(captor.getValue().getQuery()).isEqualTo("想要安静一些");
    assertThat(captor.getValue().getTopK()).isOne();
    assertThat(captor.getValue().getFilterExpression()).isNotNull();
    assertThat(captor.getValue().getFilterExpression().toString())
        .contains("courtId", "101", "status", "1", "documentType", "court_profile");
  }

  @Test
  void enhance_缺失公开状态元数据时拒绝文档() {
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(
            List.of(new Document("doc-1", "安静区", Map.of("courtId", "101", "documentType", "court_profile"))));

    RecommendationEnhancement result =
        service.enhance(List.of(candidate(1L)), 101L, "想要安静一些");

    assertThat(result.isEmpty()).isTrue();
  }

  @Test
  void enhance_缺失文档类型元数据时拒绝文档() {
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(new Document("doc-1", "安静区", Map.of("courtId", "101", "status", "1"))));

    RecommendationEnhancement result =
        service.enhance(List.of(candidate(1L)), 101L, "想要安静一些");

    assertThat(result.isEmpty()).isTrue();
  }

  @Test
  void enhance_非公开或非场地资料文档时拒绝文档() {
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(
            List.of(
                new Document(
                    "doc-1",
                    "安静区",
                    Map.of("courtId", "101", "status", "0", "documentType", "court_profile")),
                new Document(
                    "doc-2",
                    "安静区",
                    Map.of("courtId", "101", "status", "1", "documentType", "venue_profile"))));

    RecommendationEnhancement result =
        service.enhance(List.of(candidate(1L)), 101L, "想要安静一些");

    assertThat(result.isEmpty()).isTrue();
  }

  @Test
  void enhance_向量关闭时返回空增强() {
    RecommendationSemanticService disabled =
        new RecommendationSemanticService(vectorStoreProvider, false, false);

    RecommendationEnhancement result =
        disabled.enhance(List.of(candidate(1L)), 101L, "想要安静一些");

    assertThat(result.isEmpty()).isTrue();
    org.mockito.Mockito.verifyNoInteractions(vectorStore);
  }

  private SlotRecommendationItem candidate(Long slotId) {
    LocalDate date = LocalDate.of(2026, 9, 13);
    LocalDateTime startAt = LocalDateTime.of(date, java.time.LocalTime.of(18, 0));
    return new SlotRecommendationItem(
        slotId, 101L, startAt, startAt.plusHours(1), 8000, 2, 1000L, java.util.Set.of(), "规则理由");
  }
}
