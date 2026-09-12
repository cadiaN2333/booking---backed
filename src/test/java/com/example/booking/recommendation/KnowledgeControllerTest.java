package com.example.booking.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.booking.common.Result;
import com.example.booking.domain.entity.Court;
import com.example.booking.domain.entity.Venue;
import com.example.booking.service.CourtService;
import com.example.booking.service.VenueService;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KnowledgeControllerTest {

  private CourtService courtService;
  private VenueService venueService;
  private KnowledgeRebuildService rebuildService;
  private KnowledgeController controller;

  @BeforeEach
  void setUp() {
    courtService = mock(CourtService.class);
    venueService = mock(VenueService.class);
    rebuildService = mock(KnowledgeRebuildService.class);
    controller = new KnowledgeController(courtService, venueService, rebuildService);
  }

  @Test
  void rebuild_只根据已校验的商家场地构造知识源() {
    Court court = court();
    Venue venue = venue();
    when(courtService.getMine(101L)).thenReturn(court);
    when(venueService.getById(1L)).thenReturn(venue);
    when(rebuildService.rebuild(any()))
        .thenReturn(new KnowledgeRebuildResult("venue:1:court:101:profile", "hash", 1));

    KnowledgeRebuildRequest request = new KnowledgeRebuildRequest();
    request.setCourtId(101L);
    Result<KnowledgeRebuildResult> result = controller.rebuild(request);

    assertThat(result.getCode()).isZero();
    assertThat(result.getData().writtenCount()).isOne();
    ArgumentCaptor<KnowledgeSource> captor = ArgumentCaptor.forClass(KnowledgeSource.class);
    verify(rebuildService).rebuild(captor.capture());
    assertThat(captor.getValue().venueId()).isEqualTo(1L);
    assertThat(captor.getValue().courtId()).isEqualTo(101L);
    assertThat(captor.getValue().courtType()).isEqualTo("羽毛球");
  }

  private Court court() {
    Court court = new Court();
    court.setId(101L);
    court.setVenueId(1L);
    court.setName("1 号场");
    court.setType("羽毛球");
    court.setPrice(8000);
    court.setOpenTime(LocalTime.of(9, 0));
    court.setCloseTime(LocalTime.of(22, 0));
    court.setStatus(1);
    return court;
  }

  private Venue venue() {
    Venue venue = new Venue();
    venue.setId(1L);
    venue.setName("星辰羽毛球馆");
    venue.setAddress("天河区体育西路 88 号");
    venue.setStatus(1);
    return venue;
  }
}
