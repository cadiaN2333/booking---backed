-- 阶段三：Spring AI PgVector 知识库初始化脚本
-- 该脚本只在 PostgreSQL 执行，不要在 MySQL 中执行。
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS booking_vector_store (
  id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  content TEXT,
  metadata JSON,
  embedding vector(1536)
);

CREATE INDEX IF NOT EXISTS booking_vector_store_embedding_hnsw_idx
  ON booking_vector_store USING HNSW (embedding vector_cosine_ops);
