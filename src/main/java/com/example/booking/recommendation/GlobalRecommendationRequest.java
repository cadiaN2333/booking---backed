package com.example.booking.recommendation;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.LocalTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 全局场馆、场地和可预约时段推荐请求。 */
public record GlobalRecommendationRequest(
    @Size(max = 300, message = "query 最多 300 个字符") String query,
    LocalDate date,
    @Size(max = 32, message = "type 最多 32 个字符") String type,
    LocalTime startTime,
    LocalTime endTime,
    @PositiveOrZero(message = "maxPrice 不能为负数") Integer maxPrice,
    @Min(value = 1, message = "limit 最小为 1")
        @Max(value = 5, message = "limit 最大为 5")
        Integer limit) {

  private static final Pattern ISO_DATE =
      Pattern.compile("(?<!\\d)(20\\d{2})[-/]([01]?\\d)[-/]([0-3]?\\d)(?!\\d)");
  private static final Pattern MONTH_DAY =
      Pattern.compile("(?<!\\d)([01]?\\d)月([0-3]?\\d)日(?!\\d)");

  public int resolvedLimit() {
    return limit == null ? 5 : limit;
  }

  /** 查询文本中的日期优先级高于结构化 date，未识别时再默认当天。 */
  public LocalDate resolvedDate(Clock clock) {
    Clock resolvedClock = clock == null ? Clock.systemDefaultZone() : clock;
    LocalDate today = LocalDate.now(resolvedClock);
    return dateInQuery(today).orElse(date == null ? today : date);
  }

  @AssertTrue(message = "结束时间必须晚于开始时间")
  public boolean isTimeRangeValid() {
    return startTime == null || endTime == null || startTime.isBefore(endTime);
  }

  private Optional<LocalDate> dateInQuery(LocalDate today) {
    if (query == null || query.isBlank()) {
      return Optional.empty();
    }
    if (query.contains("后天")) {
      return Optional.of(today.plusDays(2));
    }
    if (query.contains("明天")) {
      return Optional.of(today.plusDays(1));
    }
    if (query.contains("今天")) {
      return Optional.of(today);
    }

    Matcher isoMatcher = ISO_DATE.matcher(query);
    if (isoMatcher.find()) {
      try {
        return Optional.of(
            LocalDate.of(
                Integer.parseInt(isoMatcher.group(1)),
                Integer.parseInt(isoMatcher.group(2)),
                Integer.parseInt(isoMatcher.group(3))));
      } catch (DateTimeException ignored) {
        return Optional.empty();
      }
    }

    Matcher monthDayMatcher = MONTH_DAY.matcher(query);
    if (monthDayMatcher.find()) {
      try {
        MonthDay monthDay =
            MonthDay.of(
                Integer.parseInt(monthDayMatcher.group(1)),
                Integer.parseInt(monthDayMatcher.group(2)));
        return Optional.of(monthDay.atYear(today.getYear()));
      } catch (DateTimeException ignored) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }
}
