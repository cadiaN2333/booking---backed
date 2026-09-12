package com.example.booking.recommendation;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 商家端知识重建请求，只允许指定已归属场地。 */
@Data
public class KnowledgeRebuildRequest {

  @NotNull(message = "courtId 必填")
  private Long courtId;
}
