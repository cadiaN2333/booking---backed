package com.example.booking.recommendation;

import com.example.booking.domain.entity.Court;
import com.example.booking.domain.entity.Venue;
import com.example.booking.domain.vo.SlotVO;
import com.example.booking.service.CourtService;
import com.example.booking.service.SlotService;
import com.example.booking.service.VenueService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 基于公开资源和实时 SlotService 快照的全局规则推荐服务。 */
@Slf4j
@Service
public class GlobalRecommendationService {

  private static final String DEFAULT_REASON = "按时间、价格与可约余量为你排序";
  private static final long EXPLICIT_TYPE_SCORE = 1_000_000L;
  private static final long QUERY_TYPE_SCORE = 100_000L;
  private static final long QUERY_TIME_SCORE = 10_000L;
  private static final long QUERY_BUDGET_SCORE = 5_000L;
  private static final long CAPACITY_SCORE = 100L;
  private static final Pattern CLOCK_TIME =
      Pattern.compile("(?<!\\d)([01]?\\d|2[0-3])(?:[:：]([0-5]\\d)|点([0-5]?\\d)?)(?!\\d)");
  private static final Pattern MONEY =
      Pattern.compile("(?<!\\d)(\\d+(?:\\.\\d+)?)\\s*(?:元|块钱|块)(?!\\d)");
  private static final Pattern AFTER_TIME =
      Pattern.compile("(?:\\d{1,2})(?:[:：]\\d{2}|点[0-5]?\\d?)\\s*(?:后|以后|之后|起)");

  private final VenueService venueService;
  private final CourtService courtService;
  private final SlotService slotService;
  private final RecommendationSemanticService semanticService;
  private final Clock clock;

  @Autowired
  public GlobalRecommendationService(
      VenueService venueService,
      CourtService courtService,
      SlotService slotService,
      RecommendationSemanticService semanticService) {
    this(venueService, courtService, slotService, semanticService, Clock.systemDefaultZone());
  }

  public GlobalRecommendationService(
      VenueService venueService,
      CourtService courtService,
      SlotService slotService,
      RecommendationSemanticService semanticService,
      Clock clock) {
    this.venueService = venueService;
    this.courtService = courtService;
    this.slotService = slotService;
    this.semanticService =
        semanticService == null ? RecommendationSemanticService.disabled() : semanticService;
    this.clock = clock == null ? Clock.systemDefaultZone() : clock;
  }

  public GlobalRecommendationResponse search(GlobalRecommendationRequest request) {
    GlobalRecommendationRequest resolvedRequest =
        request == null ? new GlobalRecommendationRequest(null, null, null, null, null, null, null) : request;
    LocalDate today = LocalDate.now(clock);
    LocalDate date = resolvedRequest.resolvedDate(clock);
    if (date.isBefore(today)) {
      return emptyResponse();
    }
    SearchCriteria criteria = SearchCriteria.from(resolvedRequest);
    List<Candidate> candidates = buildCandidates(resolvedRequest, date, criteria);
    if (candidates.isEmpty()) {
      return emptyResponse();
    }

    List<Candidate> ranked =
        candidates.stream()
            .sorted(candidateComparator())
            .limit(resolvedRequest.resolvedLimit())
            .toList();
    return enhance(ranked, resolvedRequest.query());
  }

  private List<Candidate> buildCandidates(
      GlobalRecommendationRequest request, LocalDate date, SearchCriteria criteria) {
    Map<Long, Venue> publicVenues = publicVenues();
    if (publicVenues.isEmpty()) {
      return List.of();
    }
    List<Court> courts = courtService.listOnlineByVenue(null);
    if (courts == null || courts.isEmpty()) {
      return List.of();
    }

    List<Candidate> candidates = new ArrayList<>();
    for (Court court : courts) {
      if (!isPublished(court) || court.getId() == null || court.getVenueId() == null) {
        continue;
      }
      Venue venue = publicVenues.get(court.getVenueId());
      if (venue == null || !matchesExplicitType(court, request.type())) {
        continue;
      }
      List<SlotVO> slots = slotService.listByCourtAndDate(court.getId(), date);
      if (slots == null) {
        continue;
      }
      for (SlotVO slot : slots) {
        if (!isUsableSlot(slot, court.getId(), date) || !criteria.matchesStructuredFilters(slot)) {
          continue;
        }
        boolean queryTypeMatch = matchesQueryType(court, request.query());
        boolean queryTimeMatch = criteria.queryTimeWindow() != null && criteria.queryTimeWindow().contains(slot.getStartAt());
        boolean queryBudgetMatch =
            criteria.queryMaxPrice() != null
                && slot.getPrice() != null
                && slot.getPrice() <= criteria.queryMaxPrice();
        candidates.add(
            new Candidate(
                venue,
                court,
                slot,
                score(slot, criteria, queryTypeMatch, queryTimeMatch, queryBudgetMatch),
                queryTypeMatch,
                queryTimeMatch,
                queryBudgetMatch));
      }
    }
    return decorate(deduplicateBySlotId(candidates), criteria);
  }

  private GlobalRecommendationResponse emptyResponse() {
    return new GlobalRecommendationResponse(List.of(), true);
  }

  private List<Candidate> deduplicateBySlotId(List<Candidate> candidates) {
    return candidates.stream()
        .sorted(candidateComparator())
        .collect(
            Collectors.toMap(
                candidate -> candidate.slot().getId(),
                Function.identity(),
                (preferred, ignored) -> preferred,
                LinkedHashMap::new))
        .values()
        .stream()
        .toList();
  }

  private Map<Long, Venue> publicVenues() {
    List<Venue> venues = venueService.listOnline();
    if (venues == null) {
      return Map.of();
    }
    return venues.stream()
        .filter(Objects::nonNull)
        .filter(this::isPublished)
        .filter(venue -> venue.getId() != null)
        .collect(Collectors.toMap(Venue::getId, Function.identity(), (left, right) -> left));
  }

  private List<Candidate> decorate(List<Candidate> candidates, SearchCriteria criteria) {
    if (candidates.isEmpty()) {
      return List.of();
    }
    Candidate earliest =
        candidates.stream()
            .min(
                Comparator.comparing((Candidate candidate) -> candidate.slot().getStartAt())
                    .thenComparing(candidate -> candidate.slot().getId()))
            .orElse(null);
    int maxAvailable = candidates.stream().mapToInt(candidate -> candidate.slot().getAvailable()).max().orElse(0);
    return candidates.stream()
        .map(
            candidate ->
                candidate.withPresentation(
                    tagsFor(candidate, criteria, earliest, maxAvailable),
                    reasonFor(candidate, criteria, maxAvailable)))
        .toList();
  }

  private Set<RecommendationTag> tagsFor(
      Candidate candidate, SearchCriteria criteria, Candidate earliest, int maxAvailable) {
    EnumSet<RecommendationTag> tags = EnumSet.noneOf(RecommendationTag.class);
    if (criteria.eveningPreference() && candidate.queryTimeMatch()) {
      tags.add(RecommendationTag.EVENING);
    }
    if (criteria.earliestPreference() && candidate == earliest) {
      tags.add(RecommendationTag.EARLIEST);
    }
    if (criteria.capacityPreference() && candidate.slot().getAvailable() == maxAvailable) {
      tags.add(RecommendationTag.CAPACITY);
    }
    return Set.copyOf(tags);
  }

  private String reasonFor(Candidate candidate, SearchCriteria criteria, int maxAvailable) {
    List<String> reasons = new ArrayList<>();
    if (candidate.queryTypeMatch() || criteria.explicitType()) {
      reasons.add("匹配" + safeText(candidate.court().getType(), "场地") + "类型");
    }
    if (candidate.queryTimeMatch()) {
      reasons.add("符合时间偏好");
    }
    if (candidate.queryBudgetMatch()) {
      reasons.add("价格在预算内");
    }
    if (criteria.capacityPreference() && candidate.slot().getAvailable() == maxAvailable) {
      reasons.add("当前余量较充足");
    }
    if (reasons.isEmpty()) {
      return DEFAULT_REASON;
    }
    reasons.add("当前有余量可约");
    return String.join("，", reasons);
  }

  private GlobalRecommendationResponse enhance(List<Candidate> ranked, String query) {
    List<GlobalRecommendationItem> items = ranked.stream().map(this::toItem).toList();
    Map<Long, List<Integer>> indexesByCourt = new HashMap<>();
    for (int index = 0; index < ranked.size(); index++) {
      indexesByCourt.computeIfAbsent(ranked.get(index).court().getId(), ignored -> new ArrayList<>()).add(index);
    }

    Map<Long, RecommendationEnhancement> enhancements = new HashMap<>();
    boolean degraded = false;
    for (List<Integer> indexes : indexesByCourt.values()) {
      List<SlotRecommendationItem> slotCandidates =
          indexes.stream().map(index -> toSlotItem(ranked.get(index))).toList();
      Long courtId = ranked.get(indexes.get(0)).court().getId();
      RecommendationEnhancement enhancement;
      try {
        enhancement = semanticService.enhance(slotCandidates, courtId, query);
      } catch (RuntimeException exception) {
        log.debug("全局推荐语义增强不可用 courtId={} reason={}", courtId, exception.getMessage());
        degraded = true;
        continue;
      }
      RecommendationEnhancement allowed =
          enhancement == null
              ? RecommendationEnhancement.empty()
              : enhancement.onlyFor(slotCandidates.stream().map(SlotRecommendationItem::slotId).toList());
      if (allowed.isEmpty()) {
        degraded = true;
      } else {
        for (Long slotId : allowed.reasons().keySet()) {
          enhancements.put(slotId, allowed);
        }
        for (Long slotId : allowed.tags().keySet()) {
          enhancements.put(slotId, allowed);
        }
      }
    }

    List<GlobalRecommendationItem> enhancedItems =
        items.stream().map(item -> applyEnhancement(item, enhancements.get(item.slotId()))).toList();
    return new GlobalRecommendationResponse(enhancedItems, degraded);
  }

  private GlobalRecommendationItem applyEnhancement(
      GlobalRecommendationItem item, RecommendationEnhancement enhancement) {
    if (enhancement == null || enhancement.isEmpty()) {
      return item;
    }
    Set<RecommendationTag> tags =
        java.util.stream.Stream.concat(item.tags().stream(), enhancement.tagsFor(item.slotId()).stream())
            .collect(Collectors.toUnmodifiableSet());
    return new GlobalRecommendationItem(
        item.venueId(),
        item.venueName(),
        item.venueAddress(),
        item.courtId(),
        item.courtName(),
        item.courtType(),
        item.slotId(),
        item.date(),
        item.startAt(),
        item.endAt(),
        item.price(),
        item.available(),
        item.score(),
        tags,
        enhancement.reasonFor(item.slotId()).orElse(item.reason()));
  }

  private Comparator<Candidate> candidateComparator() {
    return Comparator.comparingLong(Candidate::score)
        .reversed()
        .thenComparing(candidate -> candidate.slot().getStartAt())
        .thenComparing(candidate -> nullableInt(candidate.slot().getPrice()))
        .thenComparing(Comparator.comparingInt((Candidate candidate) -> candidate.slot().getAvailable()).reversed())
        .thenComparing(candidate -> nullableLong(candidate.venue().getId()))
        .thenComparing(candidate -> nullableLong(candidate.court().getId()))
        .thenComparing(candidate -> nullableLong(candidate.slot().getId()));
  }

  private long score(
      SlotVO slot,
      SearchCriteria criteria,
      boolean queryTypeMatch,
      boolean queryTimeMatch,
      boolean queryBudgetMatch) {
    long score = 0L;
    if (criteria.explicitType()) {
      score += EXPLICIT_TYPE_SCORE;
    }
    if (queryTypeMatch) {
      score += QUERY_TYPE_SCORE;
    }
    if (queryTimeMatch) {
      score += QUERY_TIME_SCORE;
    }
    if (queryBudgetMatch) {
      score += QUERY_BUDGET_SCORE;
    }
    if (criteria.cheapPreference()) {
      score += Math.max(0L, 1_000_000L - nullableInt(slot.getPrice()));
    }
    if (criteria.capacityPreference()) {
      score += Math.min(CAPACITY_SCORE, Math.max(0, slot.getAvailable()));
    }
    if (criteria.earliestPreference() && slot.getStartAt() != null) {
      score += 24L * 60L - slot.getStartAt().toLocalTime().toSecondOfDay() / 60L;
    }
    return score;
  }

  private GlobalRecommendationItem toItem(Candidate candidate) {
    return new GlobalRecommendationItem(
        candidate.venue().getId(),
        candidate.venue().getName(),
        candidate.venue().getAddress(),
        candidate.court().getId(),
        candidate.court().getName(),
        candidate.court().getType(),
        candidate.slot().getId(),
        candidate.slot().getBizDate(),
        candidate.slot().getStartAt(),
        candidate.slot().getEndAt(),
        candidate.slot().getPrice(),
        candidate.slot().getAvailable(),
        candidate.score(),
        candidate.tags(),
        candidate.reason());
  }

  private SlotRecommendationItem toSlotItem(Candidate candidate) {
    return new SlotRecommendationItem(
        candidate.slot().getId(),
        candidate.court().getId(),
        candidate.slot().getStartAt(),
        candidate.slot().getEndAt(),
        candidate.slot().getPrice(),
        candidate.slot().getAvailable(),
        candidate.score(),
        candidate.tags(),
        candidate.reason());
  }

  private boolean isUsableSlot(SlotVO slot, Long courtId, LocalDate date) {
    return slot != null
        && slot.getId() != null
        && Objects.equals(slot.getCourtId(), courtId)
        && Objects.equals(slot.getBizDate(), date)
        && slot.getStartAt() != null
        && slot.getEndAt() != null
        && slot.getAvailable() != null
        && slot.getAvailable() > 0
        && !isPastSlot(slot, date);
  }

  private boolean isPastSlot(SlotVO slot, LocalDate date) {
    if (!LocalDate.now(clock).equals(date)) {
      return false;
    }
    return slot.getStartAt().isBefore(LocalDateTime.now(clock));
  }

  private boolean matchesExplicitType(Court court, String requestedType) {
    return requestedType == null
        || requestedType.isBlank()
        || (court.getType() != null && court.getType().equalsIgnoreCase(requestedType.trim()));
  }

  private boolean matchesQueryType(Court court, String query) {
    return query != null
        && !query.isBlank()
        && court.getType() != null
        && query.toLowerCase(Locale.ROOT).contains(court.getType().toLowerCase(Locale.ROOT));
  }

  private boolean isPublished(Venue venue) {
    return venue != null
        && Integer.valueOf(1).equals(venue.getAuditStatus())
        && Integer.valueOf(1).equals(venue.getStatus());
  }

  private boolean isPublished(Court court) {
    return court != null
        && Integer.valueOf(1).equals(court.getAuditStatus())
        && Integer.valueOf(1).equals(court.getStatus());
  }

  private int nullableInt(Integer value) {
    return value == null ? Integer.MAX_VALUE : value;
  }

  private long nullableLong(Long value) {
    return value == null ? Long.MAX_VALUE : value;
  }

  private String safeText(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private record Candidate(
      Venue venue,
      Court court,
      SlotVO slot,
      long score,
      boolean queryTypeMatch,
      boolean queryTimeMatch,
      boolean queryBudgetMatch,
      Set<RecommendationTag> tags,
      String reason) {

    private Candidate(
        Venue venue,
        Court court,
        SlotVO slot,
        long score,
        boolean queryTypeMatch,
        boolean queryTimeMatch,
        boolean queryBudgetMatch) {
      this(venue, court, slot, score, queryTypeMatch, queryTimeMatch, queryBudgetMatch, Set.of(), DEFAULT_REASON);
    }

    private Candidate withPresentation(Set<RecommendationTag> tags, String reason) {
      return new Candidate(
          venue, court, slot, score, queryTypeMatch, queryTimeMatch, queryBudgetMatch, tags, reason);
    }
  }

  private record SearchCriteria(
      String type,
      LocalTime startTime,
      LocalTime endTime,
      Integer maxPrice,
      TimeWindow queryTimeWindow,
      Integer queryMaxPrice,
      boolean explicitType,
      boolean eveningPreference,
      boolean earliestPreference,
      boolean capacityPreference,
      boolean cheapPreference) {

    private static SearchCriteria from(GlobalRecommendationRequest request) {
      String query = request.query() == null ? "" : request.query().toLowerCase(Locale.ROOT);
      TimeWindow queryTimeWindow = TimeWindow.fromQuery(query);
      return new SearchCriteria(
          request.type(),
          request.startTime(),
          request.endTime(),
          request.maxPrice(),
          queryTimeWindow,
          parseMoney(query),
          request.type() != null && !request.type().isBlank(),
          query.contains("晚上") || query.contains("今晚") || query.contains("傍晚") || query.contains("夜间"),
          query.contains("最早") || query.contains("尽早"),
          query.contains("余量") || query.contains("空位") || query.contains("宽敞"),
          query.contains("便宜") || query.contains("实惠") || query.contains("低价"));
    }

    private boolean matchesStructuredFilters(SlotVO slot) {
      if (maxPrice != null && (slot.getPrice() == null || slot.getPrice() > maxPrice)) {
        return false;
      }
      if (queryMaxPrice != null
          && (slot.getPrice() == null || slot.getPrice() > queryMaxPrice)) {
        return false;
      }
      if (queryTimeWindow != null
          && queryTimeWindow.hard()
          && !queryTimeWindow.contains(slot.getStartAt())) {
        return false;
      }
      LocalTime start = slot.getStartAt().toLocalTime();
      LocalTime end = slot.getEndAt().toLocalTime();
      return (startTime == null || !start.isBefore(startTime))
          && (endTime == null || !end.isAfter(endTime));
    }
  }

  private record TimeWindow(LocalTime start, LocalTime end, String label, boolean hard) {

    private static TimeWindow fromQuery(String query) {
      Matcher matcher = CLOCK_TIME.matcher(query);
      List<LocalTime> times = new ArrayList<>();
      while (matcher.find()) {
        int minute = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        LocalTime time = LocalTime.of(Integer.parseInt(matcher.group(1)), minute);
        if (isEveningQuery(query) && time.getHour() < 12) {
          time = time.plusHours(12);
        }
        times.add(time);
      }
      if (!times.isEmpty() && AFTER_TIME.matcher(query).find()) {
        return new TimeWindow(times.get(0), LocalTime.MAX, "指定时段之后", true);
      }
      if (times.size() >= 2 && times.get(0).isBefore(times.get(1))) {
        return new TimeWindow(times.get(0), times.get(1), "指定时段", false);
      }
      if (times.size() == 1) {
        LocalTime end = times.get(0).equals(LocalTime.of(23, 0)) ? LocalTime.MAX : times.get(0).plusHours(1);
        return new TimeWindow(times.get(0), end, "指定时段", false);
      }
      if (isEveningQuery(query)) {
        return new TimeWindow(LocalTime.of(18, 0), LocalTime.MAX, "晚间", false);
      }
      if (query.contains("下午")) {
        return new TimeWindow(LocalTime.NOON, LocalTime.of(18, 0), "下午", false);
      }
      if (query.contains("上午") || query.contains("早上")) {
        return new TimeWindow(LocalTime.of(6, 0), LocalTime.NOON, "上午", false);
      }
      return null;
    }

    private static boolean isEveningQuery(String query) {
      return query.contains("晚上")
          || query.contains("今晚")
          || query.contains("傍晚")
          || query.contains("夜间");
    }

    private boolean contains(LocalDateTime startAt) {
      LocalTime time = startAt.toLocalTime();
      return !time.isBefore(start) && !time.isAfter(end);
    }
  }

  private static Integer parseMoney(String query) {
    Matcher matcher = MONEY.matcher(query);
    if (!matcher.find()) {
      return null;
    }
    try {
      BigDecimal cents =
          new BigDecimal(matcher.group(1)).movePointRight(2).setScale(0, RoundingMode.HALF_UP);
      if (cents.compareTo(BigDecimal.ZERO) < 0
          || cents.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
        return Integer.MAX_VALUE;
      }
      return cents.intValueExact();
    } catch (ArithmeticException | NumberFormatException ignored) {
      return null;
    }
  }
}
