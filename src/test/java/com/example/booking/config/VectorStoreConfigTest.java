package com.example.booking.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.mockito.Mockito;
import com.zaxxer.hikari.HikariDataSource;

class VectorStoreConfigTest {

  @Test
  void 百炼兼容接口使用自定义路径和向量维度() throws Exception {
    VectorStoreProperties properties = new VectorStoreProperties();
    properties.setEmbeddingApiKey("test-key");
    properties.setEmbeddingBaseUrl(
        "https://ws-example.cn-beijing.maas.aliyuncs.com/compatible-mode/v1");
    properties.setEmbeddingPath("/embeddings");
    properties.setEmbeddingModel("text-embedding-v4");
    properties.setDimensions(1536);

    EmbeddingModel model =
        new VectorStoreConfig().vectorEmbeddingModel(properties, RestClient.builder());

    OpenAiApi api = (OpenAiApi) readField(model, "openAiApi");
    OpenAiEmbeddingOptions options =
        (OpenAiEmbeddingOptions) readField(model, "defaultOptions");

    assertEquals("/embeddings", readField(api, "embeddingsPath"));
    assertEquals("text-embedding-v4", options.getModel());
    assertEquals(1536, options.getDimensions());
  }

  @Test
  void 自动初始化时不应在建表前校验不存在的表() throws Exception {
    VectorStoreProperties properties = new VectorStoreProperties();
    properties.setDimensions(1536);

    PgVectorStore vectorStore =
        (PgVectorStore)
            new VectorStoreConfig()
                .vectorStore(new JdbcTemplate(), Mockito.mock(EmbeddingModel.class), properties);

    assertFalse((Boolean) readField(vectorStore, "schemaValidation"));
    assertEquals(true, readField(vectorStore, "initializeSchema"));
  }

  @Test
  void 开启向量库时仍保留MySQL主数据源() {
    DataSourceProperties properties = new DataSourceProperties();
    properties.setUrl("jdbc:mysql://127.0.0.1:3306/booking");
    properties.setUsername("root");
    properties.setPassword("test-password");
    properties.setDriverClassName("com.mysql.cj.jdbc.Driver");

    HikariDataSource dataSource =
        (HikariDataSource) new VectorStoreConfig().mainDataSource(properties);
    try {
      assertEquals("jdbc:mysql://127.0.0.1:3306/booking", dataSource.getJdbcUrl());
    } finally {
      dataSource.close();
    }
  }

  private Object readField(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }
}
