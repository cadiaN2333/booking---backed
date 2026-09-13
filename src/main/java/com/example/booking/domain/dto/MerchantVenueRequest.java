package com.example.booking.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MerchantVenueRequest(
    @NotBlank(message = "场馆名称不能为空")
        @Size(max = 64, message = "场馆名称长度不能超过64个字符")
        String name,
    @NotBlank(message = "场馆地址不能为空")
        @Size(max = 255, message = "场馆地址长度不能超过255个字符")
        String address) {}
