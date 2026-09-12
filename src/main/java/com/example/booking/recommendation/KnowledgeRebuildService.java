package com.example.booking.recommendation;

import com.example.booking.common.BizException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** 管理公开场馆知识文档的幂等删除与重建。 */
@Service
@RequiredArgsConstructor
public class KnowledgeRebuildService {

  private final ObjectProvider<VectorStore> vectorStoreProvider;
  private final KnowledgeDocumentFactory documentFactory;

  public KnowledgeRebuildResult rebuild(KnowledgeSource source) {
    VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
    if (vectorStore == null) {
      throw new BizException(5030, "知识库未启用，请先配置 PostgreSQL 和 Embedding 服务");
    }

    KnowledgeDocument document = documentFactory.create(source);
    String filter = "documentKey == '" + escapeFilterValue(document.documentKey()) + "'";
    vectorStore.delete(filter);
    vectorStore.add(List.of(document.toSpringAiDocument()));
    return new KnowledgeRebuildResult(document.documentKey(), document.contentHash(), 1);
  }

  private String escapeFilterValue(String value) {
    return value.replace("'", "''");
  }
}
