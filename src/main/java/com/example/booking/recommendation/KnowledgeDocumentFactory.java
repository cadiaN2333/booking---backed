package com.example.booking.recommendation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/** 将公开场馆资料转换成稳定、可检索的知识文档。 */
@Component
public class KnowledgeDocumentFactory {

  public KnowledgeDocument create(KnowledgeSource source) {
    if (source == null || source.venueId() == null || source.courtId() == null) {
      throw new IllegalArgumentException("场馆和场地标识不能为空");
    }

    String documentKey = "venue:" + source.venueId() + ":court:" + source.courtId() + ":profile";
    String content = buildContent(source);
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("documentKey", documentKey);
    metadata.put("documentType", "court_profile");
    metadata.put("venueId", String.valueOf(source.venueId()));
    metadata.put("courtId", String.valueOf(source.courtId()));
    metadata.put("courtType", valueOrDefault(source.courtType(), "未分类"));
    metadata.put("status", String.valueOf(source.status() == null ? 0 : source.status()));
    metadata.put("updatedAt", valueOrDefault(source.updatedAt(), LocalDateTime.MIN).toString());
    metadata.put("contentHash", sha256(content));
    return new KnowledgeDocument(documentKey, content, Map.copyOf(metadata), sha256(content));
  }

  private String buildContent(KnowledgeSource source) {
    return "场馆：" + valueOrDefault(source.venueName(), "未命名场馆") + "\n"
        + "地址：" + valueOrDefault(source.venueAddress(), "地址待补充") + "\n"
        + "场地：" + valueOrDefault(source.courtName(), "未命名场地") + "\n"
        + "类型：" + valueOrDefault(source.courtType(), "未分类") + "\n"
        + "价格：" + formatPrice(source.price()) + " 元/时段\n"
        + "营业时间：" + valueOrDefault(source.openTime(), "未知") + " - "
        + valueOrDefault(source.closeTime(), "未知") + "\n"
        + "设施标签：" + valueOrDefault(source.facilities(), "暂无设施说明") + "\n"
        + "预约规则：" + valueOrDefault(source.rules(), "以平台当前规则为准");
  }

  private String formatPrice(Integer priceInFen) {
    if (priceInFen == null) {
      return "待询价";
    }
    return String.format(java.util.Locale.ROOT, "%.2f", priceInFen / 100.0);
  }

  private <T> T valueOrDefault(T value, T fallback) {
    return value == null ? fallback : value;
  }

  private String sha256(String content) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(content.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
    }
  }
}
