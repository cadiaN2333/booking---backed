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
    assertThat(venueBlock).contains("WHERE @venue_audit_status_exists = 0");
    assertThat(courtBlock).contains("ADD COLUMN `audit_status`");
    assertThat(courtBlock)
        .containsPattern("SET `audit_status` = 1,\\s+`status` = 1");
    assertThat(courtBlock).contains("WHERE @court_audit_status_exists = 0");
    assertThat(migration).contains("重复执行不会重置后续审核状态");
  }

  @Test
  void 静态执行迁移条件可验证首次迁移历史数据且重复执行不重置() throws IOException {
    String migration = readResource("/db/migration-v5-platform-audit.sql");

    assertThat(shouldMigrateHistory(migration, "venue", false)).isTrue();
    assertThat(shouldMigrateHistory(migration, "venue", true)).isFalse();
    assertThat(shouldMigrateHistory(migration, "court", false)).isTrue();
    assertThat(shouldMigrateHistory(migration, "court", true)).isFalse();
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

  private boolean shouldMigrateHistory(String migration, String tableName, boolean alreadyMigrated) {
    String marker = "@" + tableName + "_audit_status_exists";
    String updatePattern = "UPDATE `" + tableName
        + "`\\s+SET `audit_status` = 1,\\s+`status` = 1\\s+WHERE " + marker + " = 0";
    assertThat(migration).containsPattern(updatePattern);
    return !alreadyMigrated && migration.contains("WHERE " + marker + " = 0");
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
