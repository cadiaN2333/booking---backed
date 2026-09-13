package com.example.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PlatformAuditMigrationTest {

  @Test
  void schema中的新资源默认下架() throws IOException {
    String schema = readResource("/db/schema.sql");

    assertThat(schema)
        .containsPattern(
            "`status`\\s+TINYINT\\s+NOT NULL DEFAULT 0 COMMENT '1已上架 0下架'");
    assertThat(schema)
        .containsPattern(
            "`status`\\s+TINYINT\\s+NOT NULL DEFAULT 0 COMMENT '1已上架 0下架'");
  }

  @Test
  void migration幂等修改默认值且只在首次加审核字段时迁移历史数据() throws IOException {
    String migration = readResource("/db/migration-v5-platform-audit.sql");

    assertThat(migration)
        .contains("MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0");
    assertThat(migration).contains("WHERE @venue_audit_status_exists = 0");
    assertThat(migration).contains("WHERE @court_audit_status_exists = 0");
    assertThat(migration).contains("重复执行不会重置后续审核状态");
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
