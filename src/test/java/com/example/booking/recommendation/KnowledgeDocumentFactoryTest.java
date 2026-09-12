package com.example.booking.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgeDocumentFactoryTest {

  @Test
  void create_生成稳定文档键和完整元数据() {
    KnowledgeSource source = source();

    KnowledgeDocument document = new KnowledgeDocumentFactory().create(source);

    assertThat(document.documentKey()).isEqualTo("venue:1:court:101:profile");
    assertThat(document.content()).contains("星辰羽毛球馆", "1 号场", "羽毛球", "安静区");
    assertThat(document.metadata())
        .containsEntry("documentType", "court_profile")
        .containsEntry("venueId", "1")
        .containsEntry("courtId", "101")
        .containsEntry("courtType", "羽毛球")
        .containsEntry("status", "1")
        .containsEntry("updatedAt", "2026-09-12T10:00");
    assertThat(document.contentHash()).hasSize(64);
  }

  @Test
  void create_相同输入重复生成内容哈希一致() {
    KnowledgeDocumentFactory factory = new KnowledgeDocumentFactory();

    KnowledgeDocument first = factory.create(source());
    KnowledgeDocument second = factory.create(source());

    assertThat(second.contentHash()).isEqualTo(first.contentHash());
    assertThat(second.content()).isEqualTo(first.content());
  }

  @Test
  void create_生成稳定的UUID文档标识并保留业务稳定键() {
    KnowledgeDocumentFactory factory = new KnowledgeDocumentFactory();

    var first = factory.create(source()).toSpringAiDocument();
    var second = factory.create(source()).toSpringAiDocument();
    String documentKey = "venue:1:court:101:profile";

    UUID.fromString(first.getId());
    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(first.getId())
        .isEqualTo(UUID.nameUUIDFromBytes(documentKey.getBytes(StandardCharsets.UTF_8)).toString());
    assertThat(first.getMetadata()).containsEntry("documentKey", documentKey);
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
