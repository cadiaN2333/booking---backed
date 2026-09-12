package com.example.booking.recommendation;

import java.util.Map;
import org.springframework.ai.document.Document;

/** 知识库文档及其稳定标识、内容指纹。 */
public record KnowledgeDocument(
    String documentKey, String content, Map<String, Object> metadata, String contentHash) {

  /** 转为 Spring AI 文档，显式使用稳定键避免重复重建产生随机文档 ID。 */
  public Document toSpringAiDocument() {
    return new Document(documentKey, content, metadata);
  }
}
