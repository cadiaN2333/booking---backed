package com.example.booking.recommendation;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;

/** 知识库文档及其稳定标识、内容指纹。 */
public record KnowledgeDocument(
    String documentKey, String content, Map<String, Object> metadata, String contentHash) {

  /** 转为 Spring AI 文档，将业务稳定键转换为稳定 UUID，业务键仍保留在元数据中。 */
  public Document toSpringAiDocument() {
    String documentId = UUID.nameUUIDFromBytes(documentKey.getBytes(StandardCharsets.UTF_8)).toString();
    return new Document(documentId, content, metadata);
  }
}
