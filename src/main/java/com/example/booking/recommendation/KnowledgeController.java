package com.example.booking.recommendation;

import com.example.booking.common.BizException;
import com.example.booking.common.Result;
import com.example.booking.domain.entity.Court;
import com.example.booking.domain.entity.Venue;
import com.example.booking.service.CourtService;
import com.example.booking.service.VenueService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商家端公开知识资料重建入口，不触碰预约和库存数据。 */
@RestController
@RequestMapping("/merchant/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

  private final CourtService courtService;
  private final VenueService venueService;
  private final KnowledgeRebuildService rebuildService;

  @PostMapping("/rebuild")
  public Result<KnowledgeRebuildResult> rebuild(
      @Valid @RequestBody KnowledgeRebuildRequest request) {
    Court court = courtService.getMine(request.getCourtId());
    Venue venue = venueService.getById(court.getVenueId());
    if (venue == null) {
      throw new BizException("场馆不存在");
    }
    KnowledgeSource source =
        new KnowledgeSource(
            venue.getId(),
            venue.getName(),
            venue.getAddress(),
            court.getId(),
            court.getName(),
            court.getType(),
            court.getPrice(),
            court.getOpenTime(),
            court.getCloseTime(),
            court.getStatus(),
            facilitiesFor(court),
            "预约后锁定 15 分钟，未确认会自动释放；具体规则以平台页面为准",
            latestTime(venue.getCreateTime(), court.getCreateTime()));
    return Result.ok(rebuildService.rebuild(source));
  }

  private String facilitiesFor(Court court) {
    if ("羽毛球".equals(court.getType())) {
      return "灯光场地、休息区、更衣设施，以场馆实际配置为准";
    }
    if ("会议室".equals(court.getType())) {
      return "桌椅、会议空间，投影及网络以场馆实际配置为准";
    }
    if ("自习室".equals(court.getType())) {
      return "安静学习区、座位，以场馆实际配置为准";
    }
    return "设施信息以场馆实际配置为准";
  }

  private LocalDateTime latestTime(LocalDateTime first, LocalDateTime second) {
    if (first == null) {
      return second;
    }
    if (second == null) {
      return first;
    }
    return first.isAfter(second) ? first : second;
  }
}
