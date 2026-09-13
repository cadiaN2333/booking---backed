package com.example.booking.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理员驳回审核资源时提交的原因。 */
public record AuditDecisionRequest(
    @NotBlank(message = "驳回原因不能为空")
        @Size(max = 500, message = "驳回原因不能超过500个字符")
        String reason) {}
