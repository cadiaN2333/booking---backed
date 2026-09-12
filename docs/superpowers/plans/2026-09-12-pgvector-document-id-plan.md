# PgVector 文档 ID 修复实施计划

> **给代理开发者：** 必须使用 `superpowers:subagent-driven-development` 或 `superpowers:executing-plans` 按任务逐项实施。本计划使用复选框跟踪，每一步都先验证再进入下一步。

**目标：** 修复知识库重建写入 PgVector 时将业务稳定键误当作 UUID 的问题，使 `courtId=101` 的真实重建成功。

**架构：** 业务稳定键继续作为 metadata 中的 `documentKey`，用于删除旧文档；Spring AI `Document.id` 改为由稳定键生成的名称型 UUID。名称型 UUID 保证同一场地重复重建得到相同物理 ID，不需要新增表或映射关系。

**技术栈：** Java 17（当前以 JDK 21 运行）、Spring Boot、Spring AI 1.0.9、PgVector、JUnit 5、AssertJ、Mockito、Maven。

---

## 文件结构

- 修改：`src/test/java/com/example/booking/recommendation/KnowledgeDocumentFactoryTest.java`
  - 增加 Spring AI 文档 ID 的回归测试，验证合法 UUID、确定性和稳定键 metadata。
- 修改：`src/main/java/com/example/booking/recommendation/KnowledgeDocument.java`
  - 将稳定键转换为名称型 UUID 后构造 `Document`。
- 修改：`src/test/java/com/example/booking/recommendation/KnowledgeRebuildServiceTest.java`
  - 将旧的业务键 ID 断言改为合法 UUID 和稳定键 metadata 断言。
- 不修改：`src/main/java/com/example/booking/recommendation/KnowledgeDocumentFactory.java`
  - 继续负责业务稳定键、内容和 metadata 的生成。
- 不修改：`src/main/java/com/example/booking/recommendation/KnowledgeRebuildService.java`
  - 继续按 metadata 稳定键删除后写入文档。

## 实施任务

### 任务 1：编写能复现问题的失败测试

**文件：** `src/test/java/com/example/booking/recommendation/KnowledgeDocumentFactoryTest.java`

- [ ] **步骤 1：** 增加以下导入：

```java
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.ai.document.Document;
```

- [ ] **步骤 2：** 在现有测试类中增加：

```java
@Test
void toSpringAiDocument_使用稳定键生成合法且确定的UUID() {
  KnowledgeDocumentFactory factory = new KnowledgeDocumentFactory();

  Document first = factory.create(source()).toSpringAiDocument();
  Document second = factory.create(source()).toSpringAiDocument();
  String expectedId = UUID.nameUUIDFromBytes(
      "venue:1:court:101:profile".getBytes(StandardCharsets.UTF_8)).toString();

  assertThatCode(() -> UUID.fromString(first.getId())).doesNotThrowAnyException();
  assertThat(first.getId()).isEqualTo(expectedId);
  assertThat(second.getId()).isEqualTo(first.getId());
  assertThat(first.getMetadata()).containsEntry("documentKey", "venue:1:court:101:profile");
}
```

- [ ] **步骤 3：** 运行新增测试，确认它因当前实现仍使用业务键作为 id 而失败：

```powershell
$env:MAVEN_ARGS = '-Dmaven.repo.local=D:/booking-linked/.m2'
& 'D:\apache-maven-3.9.16\bin\mvn.cmd' '-Dtest=KnowledgeDocumentFactoryTest#toSpringAiDocument_使用稳定键生成合法且确定的UUID' test
Remove-Item Env:MAVEN_ARGS
```

预期：测试因 id 不是合法 UUID 或 id 不等于名称型 UUID 而失败，不能因测试编译错误失败。

### 任务 2：实现最小生产代码修复

**文件：** `src/main/java/com/example/booking/recommendation/KnowledgeDocument.java`

- [ ] **步骤 1：** 增加导入：

```java
import java.nio.charset.StandardCharsets;
import java.util.UUID;
```

- [ ] **步骤 2：** 将 `toSpringAiDocument()` 替换为：

```java
/** 转为 Spring AI 文档，使用稳定键派生 UUID 并保留业务稳定键元数据。 */
public Document toSpringAiDocument() {
  String documentId = UUID.nameUUIDFromBytes(documentKey.getBytes(StandardCharsets.UTF_8))
      .toString();
  return new Document(documentId, content, metadata);
}
```

- [ ] **步骤 3：** 重跑任务 1 的测试，确认通过：

```powershell
$env:MAVEN_ARGS = '-Dmaven.repo.local=D:/booking-linked/.m2'
& 'D:\apache-maven-3.9.16\bin\mvn.cmd' '-Dtest=KnowledgeDocumentFactoryTest#toSpringAiDocument_使用稳定键生成合法且确定的UUID' test
Remove-Item Env:MAVEN_ARGS
```

预期：测试通过。

### 任务 3：验证重建逻辑不回归

**文件：** `src/test/java/com/example/booking/recommendation/KnowledgeRebuildServiceTest.java`

- [ ] **步骤 1：** 将 `rebuild_先删除旧版本再写入新文档()` 中的旧断言：

```java
assertThat(document.getId()).isEqualTo("venue:1:court:101:profile");
```

替换为：

```java
assertThatCode(() -> java.util.UUID.fromString(document.getId()))
    .doesNotThrowAnyException();
assertThat(document.getMetadata())
    .containsEntry("documentKey", "venue:1:court:101:profile")
    .containsEntry("courtId", "101");
```

如文件顶部尚未导入 `assertThatCode`，增加：

```java
import static org.assertj.core.api.Assertions.assertThatCode;
```

- [ ] **步骤 2：** 运行推荐模块相关测试：

```powershell
$env:MAVEN_ARGS = '-Dmaven.repo.local=D:/booking-linked/.m2'
& 'D:\apache-maven-3.9.16\bin\mvn.cmd' '-Dtest=KnowledgeDocumentFactoryTest,KnowledgeRebuildServiceTest' test
Remove-Item Env:MAVEN_ARGS
```

预期：所有相关测试通过；删除调用仍使用 `documentKey == 'venue:1:court:101:profile'`。

### 任务 4：全量验证、打包并提交

- [ ] **步骤 1：** 运行完整测试：

```powershell
$env:MAVEN_ARGS = '-Dmaven.repo.local=D:/booking-linked/.m2'
& 'D:\apache-maven-3.9.16\bin\mvn.cmd' test
Remove-Item Env:MAVEN_ARGS
```

预期：测试全部通过，失败数为 0，错误数为 0。

- [ ] **步骤 2：** 打包生产 JAR：

```powershell
$env:MAVEN_ARGS = '-Dmaven.repo.local=D:/booking-linked/.m2'
& 'D:\apache-maven-3.9.16\bin\mvn.cmd' '-DskipTests' package
Remove-Item Env:MAVEN_ARGS
```

预期：生成 `target/booking-backend-1.0.0.jar`。

- [ ] **步骤 3：** 检查并提交，只加入本次三个代码文件：

```powershell
git diff --check
git status --short
git add src/main/java/com/example/booking/recommendation/KnowledgeDocument.java src/test/java/com/example/booking/recommendation/KnowledgeDocumentFactoryTest.java src/test/java/com/example/booking/recommendation/KnowledgeRebuildServiceTest.java
git diff --cached --check
git commit -m "修复PgVector文档UUID写入"
```

预期：不提交用户的 `login-request.json`、`rebuild-request.json`，不执行 `git push`。

### 任务 5：真实接口验收

- [ ] **步骤 1：** 停止旧 JAR，使用任务 4 生成的新 JAR 重启；保留已验证可用的千问配置和 `/embeddings` 路径。
- [ ] **步骤 2：** 使用商家 Token 调用：

```text
POST http://localhost:8080/api/merchant/knowledge/rebuild
请求体：{"courtId":101}
```

- [ ] **步骤 3：** 验收返回 `code:0`、`documentKey=venue:1:court:101:profile` 和 `writtenCount=1`，并确认日志不再出现 `Invalid UUID string`。

## 回滚方案

如果测试或真实接口验证失败，保留失败日志和未提交差异，暂不推送远程；不删除已有向量数据、不重置数据库、不触碰用户的两个请求 JSON 文件。
