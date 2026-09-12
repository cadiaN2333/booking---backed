package com.example.booking.config;

import javax.sql.DataSource;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/** 可选的 PgVector 基础设施，不与 MySQL 交易数据源混用。 */
@Configuration
@ConditionalOnProperty(name = "booking.vector.enabled", havingValue = "true")
@EnableConfigurationProperties(VectorStoreProperties.class)
public class VectorStoreConfig {

  @Bean(name = "vectorDataSource")
  public DataSource vectorDataSource(VectorStoreProperties properties) {
    return DataSourceBuilder.create()
        .url(properties.getDatasourceUrl())
        .username(properties.getDatasourceUsername())
        .password(properties.getDatasourcePassword())
        .build();
  }

  @Bean(name = "vectorJdbcTemplate")
  public JdbcTemplate vectorJdbcTemplate(@Qualifier("vectorDataSource") DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }

  @Bean(name = "vectorEmbeddingModel")
  public EmbeddingModel vectorEmbeddingModel(
      VectorStoreProperties properties, RestClient.Builder restClientBuilder) {
    if (properties.getEmbeddingApiKey() == null || properties.getEmbeddingApiKey().isBlank()) {
      throw new BeanCreationException(
          "vectorEmbeddingModel", "BOOKING_VECTOR_ENABLED=true 时必须配置 OPENAI_API_KEY");
    }
    OpenAiApi api =
        OpenAiApi.builder()
            .baseUrl(properties.getEmbeddingBaseUrl())
            .apiKey(properties.getEmbeddingApiKey())
            .restClientBuilder(restClientBuilder)
            .build();
    return new OpenAiEmbeddingModel(
        api,
        MetadataMode.EMBED,
        OpenAiEmbeddingOptions.builder().model(properties.getEmbeddingModel()).build());
  }

  @Bean
  public VectorStore vectorStore(
      @Qualifier("vectorJdbcTemplate") JdbcTemplate jdbcTemplate,
      @Qualifier("vectorEmbeddingModel") EmbeddingModel embeddingModel,
      VectorStoreProperties properties) {
    return PgVectorStore.builder(jdbcTemplate, embeddingModel)
        .dimensions(properties.getDimensions())
        .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
        .indexType(PgVectorStore.PgIndexType.HNSW)
        .schemaName(properties.getSchemaName())
        .vectorTableName(properties.getTableName())
        .vectorTableValidationsEnabled(true)
        .initializeSchema(true)
        .maxDocumentBatchSize(properties.getMaxDocumentBatchSize())
        .build();
  }
}
