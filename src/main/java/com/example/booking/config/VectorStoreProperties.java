package com.example.booking.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 阶段三向量知识库配置，默认关闭，避免影响现有交易服务。 */
@Data
@ConfigurationProperties(prefix = "booking.vector")
public class VectorStoreProperties {

  private boolean enabled;
  private String datasourceUrl;
  private String datasourceUsername;
  private String datasourcePassword;
  private String embeddingApiKey;
  private String embeddingBaseUrl;
  private String embeddingPath = "/v1/embeddings";
  private String embeddingModel;
  private int dimensions = 1536;
  private String schemaName = "public";
  private String tableName = "booking_vector_store";
  private int maxDocumentBatchSize = 100;
}
