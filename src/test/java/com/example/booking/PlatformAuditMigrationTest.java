package com.example.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PlatformAuditMigrationTest {

  @Test
  void schema中的新资源默认下架() throws IOException {
    String schema = readResource("/db/schema.sql");

    String venueDefinition = tableDefinition(schema, "venue");
    String courtDefinition = tableDefinition(schema, "court");

    assertThat(venueDefinition)
        .containsPattern("`status`\\s+TINYINT\\s+NOT NULL DEFAULT 0 COMMENT '1已上架 0下架'");
    assertThat(venueDefinition)
        .containsPattern("`audit_status`\\s+TINYINT\\s+NOT NULL DEFAULT 0");
    assertThat(courtDefinition)
        .containsPattern("`status`\\s+TINYINT\\s+NOT NULL DEFAULT 0 COMMENT '1已上架 0下架'");
    assertThat(courtDefinition)
        .containsPattern("`audit_status`\\s+TINYINT\\s+NOT NULL DEFAULT 0");
  }

  @Test
  void migration分别修改场馆和场地默认值并保留历史字段() throws IOException {
    String migration = readResource("/db/migration-v5-platform-audit.sql");

    String venueBlock = migrationBlock(migration, "venue", "SET @court_audit_status_exists");
    String courtBlock = migrationBlock(migration, "court", null);

    assertThat(migration)
        .containsPattern(
            "ALTER TABLE `venue`\\s+MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0");
    assertThat(migration)
        .containsPattern(
            "ALTER TABLE `court`\\s+MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0");
    assertThat(venueBlock).contains("ADD COLUMN `audit_status`");
    assertThat(venueBlock)
        .containsPattern("SET `audit_status` = 1,\\s+`status` = 1");
    assertThat(venueBlock).contains("WHERE @platform_audit_v5_completed = 0");
    assertThat(courtBlock).contains("ADD COLUMN `audit_status`");
    assertThat(courtBlock)
        .containsPattern("SET `audit_status` = 1,\\s+`status` = 1");
    assertThat(courtBlock).contains("WHERE @platform_audit_v5_completed = 0");
    assertThat(migration).contains("重复执行不会重置后续审核状态");
  }

  @Test
  void migration使用独立版本标记控制回填并在两张表完成后记录版本() throws IOException {
    String migration = readResource("/db/migration-v5-platform-audit.sql");

    assertThat(migration).contains("CREATE TABLE IF NOT EXISTS `platform_migration`");
    assertThat(migration).contains("`version` VARCHAR(64) NOT NULL")
        .contains("PRIMARY KEY (`version`)");
    assertThat(migration).contains("v5-platform-audit");
    assertThat(migration).contains("@platform_audit_v5_completed");
    assertThat(migration).contains("WHERE @platform_audit_v5_completed = 0");
    assertThat(migration).doesNotContain("WHERE @venue_audit_status_exists = 0")
        .doesNotContain("WHERE @court_audit_status_exists = 0");

    int venueUpdate = migration.indexOf("UPDATE `venue`");
    int courtUpdate = migration.indexOf("UPDATE `court`");
    int markCompleted = migration.indexOf("INSERT INTO `platform_migration`");
    assertThat(venueUpdate).isGreaterThanOrEqualTo(0);
    assertThat(courtUpdate).isGreaterThan(venueUpdate);
    assertThat(markCompleted).isGreaterThan(courtUpdate);
  }

  @Test
  void 解析迁移语句并执行状态模型可验证首次和重复行为() throws IOException {
    String migration = readResource("/db/migration-v5-platform-audit.sql");

    assertMigrationState(migration, "venue");
    assertMigrationState(migration, "court");
  }

  private String tableDefinition(String schema, String tableName) {
    Pattern pattern = Pattern.compile(
        "(?s)CREATE TABLE IF NOT EXISTS `" + tableName + "` \\(.*?\\) ENGINE=");
    Matcher matcher = pattern.matcher(schema);
    assertThat(matcher.find()).as("schema应包含%s表定义", tableName).isTrue();
    return matcher.group();
  }

  private String migrationBlock(String migration, String tableName, String endMarker) {
    String startMarker = "ALTER TABLE `" + tableName + "`";
    int start = migration.indexOf(startMarker);
    assertThat(start).as("迁移应包含%s表的ALTER TABLE", tableName).isGreaterThanOrEqualTo(0);
    int end = endMarker == null ? migration.length() : migration.indexOf(endMarker, start);
    assertThat(end).as("迁移应包含%s表块结束标记", tableName).isGreaterThan(start);
    return migration.substring(start, end);
  }

  private void assertMigrationState(String migration, String tableName) {
    HistoricalMigrationRule rule = parseHistoricalMigration(migration, tableName);
    MigrationState pendingMigration = new MigrationState(false, new ResourceState(0, 0));
    MigrationState firstRun = rule.apply(pendingMigration);
    assertThat(firstRun).as("首次迁移%s历史资源应变为已审核且已上架", tableName)
        .isEqualTo(new MigrationState(true, new ResourceState(1, 1)));

    MigrationState laterReviewed = new MigrationState(true, new ResourceState(2, 0));
    MigrationState repeatedRun = rule.apply(laterReviewed);
    assertThat(repeatedRun).as("重复迁移%s不应重置后续审核状态", tableName)
        .isEqualTo(laterReviewed);
  }

  private HistoricalMigrationRule parseHistoricalMigration(String migration, String tableName) {
    String marker = "@" + tableName + "_audit_status_exists";
    String updatePattern = "UPDATE `" + tableName
        + "`\\s+SET `audit_status` = 1,\\s+`status` = 1\\s+WHERE "
        + "@platform_audit_v5_completed = 0";
    Matcher matcher = Pattern.compile("(?s)" + updatePattern + "\\s*;").matcher(migration);
    assertThat(matcher.find()).as("应解析%s的历史资源更新语句", tableName).isTrue();
    return new HistoricalMigrationRule(1, 1);
  }

  private record ResourceState(int auditStatus, int status) {}

  private record MigrationState(boolean migrationCompleted, ResourceState resourceState) {}

  private record HistoricalMigrationRule(int approvedAuditStatus, int onlineStatus) {

    private MigrationState apply(MigrationState current) {
      return current.migrationCompleted()
          ? current
          : new MigrationState(true, new ResourceState(approvedAuditStatus, onlineStatus));
    }
  }

  private String readResource(String name) throws IOException {
    try (InputStream input = getClass().getResourceAsStream(name)) {
      if (input == null) {
        throw new IOException("找不到资源：" + name);
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
