package com.example.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemoDataSqlTest {

  @Test
  void 演示场馆和场地显式初始化为已审核且已上架() throws IOException {
    String sql = readResource("/db/data.sql");

    assertDemoResourcesAreApprovedAndOnline(sql, "venue");
    assertDemoResourcesAreApprovedAndOnline(sql, "court");
  }

  private void assertDemoResourcesAreApprovedAndOnline(String sql, String tableName) {
    String statement = statementFor(sql, tableName);
    assertThat(statement).contains("`status`");
    assertThat(statement).contains("`audit_status`");

    String values = statement.substring(statement.indexOf("VALUES") + "VALUES".length(),
        statement.indexOf("ON DUPLICATE KEY UPDATE"));
    List<String> expectedIds = "venue".equals(tableName)
        ? List.of("1", "2", "3")
        : List.of("101", "102", "103", "201", "202", "301");
    for (String id : expectedIds) {
      assertThat(values).containsPattern("(?m)^\\s*\\(" + id + ",");
    }
    for (String line : values.split("\\R")) {
      if (!line.trim().startsWith("(")) {
        continue;
      }
      assertThat(line)
          .as("%s 演示数据每一行都应显式指定 status=1、audit_status=1", tableName)
          .containsPattern(",\\s*1\\s*,\\s*1\\s*\\),?\\s*$");
    }

    assertThat(statement)
        .contains("`name` = VALUES(`name`)");
    String duplicateUpdate = statement.substring(statement.indexOf("ON DUPLICATE KEY UPDATE"));
    assertThat(duplicateUpdate)
        .doesNotContain("`status`")
        .doesNotContain("`audit_status`")
        .doesNotContain("`merchant_id`");
  }

  private String statementFor(String sql, String tableName) {
    String marker = "INSERT INTO `" + tableName + "`";
    int start = sql.indexOf(marker);
    assertThat(start).as("data.sql应包含%s演示数据", tableName).isGreaterThanOrEqualTo(0);
    int end = sql.indexOf(';', start);
    assertThat(end).as("%s演示数据应以分号结束", tableName).isGreaterThan(start);
    return sql.substring(start, end);
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
