package com.example.booking.domain.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public class MerchantCourtRequest {

  @NotNull(message = "场馆不能为空")
  private Long venueId;

  @NotBlank(message = "场地名称不能为空")
  @Size(max = 64, message = "场地名称长度不能超过64个字符")
  private String name;

  @NotBlank(message = "场地类型不能为空")
  @Size(max = 32, message = "场地类型长度不能超过32个字符")
  private String type;

  @NotNull(message = "价格不能为空")
  @Positive(message = "价格必须大于0")
  private Integer price;

  @NotNull(message = "营业开始时间不能为空")
  private LocalTime openTime;

  @NotNull(message = "营业结束时间不能为空")
  private LocalTime closeTime;

  @NotNull(message = "时段长度不能为空")
  @Min(value = 15, message = "时段长度必须在15至240分钟之间")
  @Max(value = 240, message = "时段长度必须在15至240分钟之间")
  private Integer slotMinutes;

  public MerchantCourtRequest() {}

  public MerchantCourtRequest(
      Long venueId,
      String name,
      String type,
      Integer price,
      LocalTime openTime,
      LocalTime closeTime,
      Integer slotMinutes) {
    this.venueId = venueId;
    this.name = name;
    this.type = type;
    this.price = price;
    this.openTime = openTime;
    this.closeTime = closeTime;
    this.slotMinutes = slotMinutes;
  }

  @AssertTrue(message = "营业开始时间必须早于结束时间")
  public boolean isOpenTimeBeforeCloseTime() {
    return openTime == null || closeTime == null || openTime.isBefore(closeTime);
  }

  @AssertTrue(message = "时段长度必须是15分钟的倍数")
  public boolean isSlotMinutesMultipleOf15() {
    return slotMinutes == null || slotMinutes % 15 == 0;
  }

  public Long getVenueId() {
    return venueId;
  }

  public void setVenueId(Long venueId) {
    this.venueId = venueId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Integer getPrice() {
    return price;
  }

  public void setPrice(Integer price) {
    this.price = price;
  }

  public LocalTime getOpenTime() {
    return openTime;
  }

  public void setOpenTime(LocalTime openTime) {
    this.openTime = openTime;
  }

  public LocalTime getCloseTime() {
    return closeTime;
  }

  public void setCloseTime(LocalTime closeTime) {
    this.closeTime = closeTime;
  }

  public Integer getSlotMinutes() {
    return slotMinutes;
  }

  public void setSlotMinutes(Integer slotMinutes) {
    this.slotMinutes = slotMinutes;
  }
}
