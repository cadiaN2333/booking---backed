# PgVector 文档 ID 修复设计

## 背景

阶段三知识库重建已经能够完成场地数据查询和 Embedding 调用，但写入 PgVector 时失败。Spring AI 的 `PgVectorStore` 会将 `Document.id` 转换为 PostgreSQL UUID，而当前实现把业务稳定键 `venue:1:court:101:profile` 直接作为 `Document.id`，导致 `Invalid UUID string`。

## 目标

修复知识文档写入失败，同时保留以下行为：

1. 每个场地继续使用稳定键 `venue:{venueId}:court:{courtId}:profile`。
2. 稳定键继续写入 metadata 的 `documentKey` 字段，用于删除旧版本。
3. 同一个稳定键每次生成相同的合法 UUID，便于幂等重建和排查。
4. 不修改 PostgreSQL 表结构，不影响 MySQL 交易数据和实时库存。

## 方案

在 `KnowledgeDocument.toSpringAiDocument()` 中根据 `documentKey` 生成名称型 UUID：

```text
UUID.nameUUIDFromBytes(documentKey.getBytes(StandardCharsets.UTF_8))
```

生成结果作为 Spring AI `Document.id`，原稳定键继续保存在 metadata。知识库重建服务仍按 metadata 中的 `documentKey` 删除旧文档，再新增当前文档。

选择名称型 UUID 而不是随机 UUID 的原因是：相同场地的重建得到相同 ID，能够减少重复数据风险，并且不需要新增数据库映射表。业务稳定键不再承担 PgVector 的 UUID 字段职责。

## 数据流

```text
场地业务数据
  -> KnowledgeDocumentFactory
  -> documentKey + metadata
  -> 名称型 UUID 作为 Document.id
  -> EmbeddingModel 生成向量
  -> PgVectorStore 写入
```

删除流程仍使用：

```text
documentKey == 'venue:1:court:101:profile'
```

因此删除条件与物理 UUID 解耦。

## 测试与验收

增加或调整单元测试，验证：

1. `Document.id` 可以被 `UUID.fromString` 解析。
2. 相同 `documentKey` 生成相同 `Document.id`。
3. `Document.metadata.documentKey` 保留业务稳定键。
4. 重建服务仍先删除旧文档再写入新文档。
5. 全部后端测试通过，并使用真实后端接口完成一次 `courtId=101` 重建，返回 `code:0` 和 `writtenCount:1`。

## 非目标

本次不新增推荐查询接口、不引入聊天模型、不修改订单并发逻辑、不迁移现有业务表，也不改变千问 API 配置。
