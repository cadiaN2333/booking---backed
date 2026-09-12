package com.example.booking.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.web.client.RestClient;

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

  private Object readField(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }
}
