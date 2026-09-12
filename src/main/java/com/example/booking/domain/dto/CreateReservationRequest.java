package com.example.booking.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import lombok.Data;

@Data
public class CreateReservationRequest implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 进入确认页时下发的幂等令牌 */
  @NotBlank(message = "缺少幂等令牌")
  private String token;

  @NotNull(message = "时段不能为空")
  private Long slotId;

  // userId 不再由前端传入，统一从登录态（UserContext）取，避免越权替别人下单
}
