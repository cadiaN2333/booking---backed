package com.example.booking.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.booking.common.BizException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

class KnowledgeRebuildServiceTest {

  private VectorStore vectorStore;
  private ObjectProvider<VectorStore> vectorStoreProvider;
  private KnowledgeRebuildService service;

  @BeforeEach
  void setUp() {
    vectorStore = mock(VectorStore.class);
    vectorStoreProvider = mock(ObjectProvider.class);
    when(vectorStoreProvider.getIfAvailable()).thenReturn(vectorStore);
    service = new KnowledgeRebuildService(vectorStoreProvider, new KnowledgeDocumentFactory());
  }

  @Test
  void rebuild_先删除旧版本再写入新文档() {
    KnowledgeRebuildResult result = service.rebuild(source());

    InOrder order = inOrder(vectorStore);
    order.verify(vectorStore).delete("documentKey == 'venue:1:court:101:profile'");
    ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
    order.verify(vectorStore).add(captor.capture());
    assertThat(captor.getValue()).singleElement().satisfies(document -> {
      assertThat(document.getId()).isEqualTo("venue:1:court:101:profile");
      assertThat(document.getMetadata()).containsEntry("courtId", "101");
    });
    assertThat(result.documentKey()).isEqualTo("venue:1:court:101:profile");
    assertThat(result.writtenCount()).isOne();
  }

  @Test
  void rebuild_向量库未启用时返回明确业务错误() {
    when(vectorStoreProvider.getIfAvailable()).thenReturn(null);

    assertThatThrownBy(() -> service.rebuild(source()))
        .isInstanceOf(BizException.class)
        .hasMessage("知识库未启用，请先配置 PostgreSQL 和 Embedding 服务");
    verify(vectorStore, org.mockito.Mockito.never()).add(anyList());
  }

  private KnowledgeSource source() {
    return new KnowledgeSource(
        1L,
        "星辰羽毛球馆",
        "天河区体育西路 88 号",
        101L,
        "1 号场",
        "羽毛球",
        8000,
        LocalTime.of(9, 0),
        LocalTime.of(22, 0),
        1,
        "安静区、淋浴、更衣室",
        "预约后锁定 15 分钟，未确认会自动释放",
        LocalDateTime.of(2026, 9, 12, 10, 0));
  }
}
