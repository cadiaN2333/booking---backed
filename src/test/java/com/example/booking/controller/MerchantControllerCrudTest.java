package com.example.booking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.booking.common.Result;
import com.example.booking.domain.dto.MerchantCourtRequest;
import com.example.booking.domain.dto.MerchantVenueRequest;
import com.example.booking.domain.entity.Court;
import com.example.booking.domain.entity.Venue;
import com.example.booking.service.CourtService;
import com.example.booking.service.SlotService;
import com.example.booking.service.VenueService;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MerchantControllerCrudTest {

  private VenueService venueService;
  private CourtService courtService;
  private MerchantController controller;

  @BeforeEach
  void setUp() {
    venueService = mock(VenueService.class);
    courtService = mock(CourtService.class);
    controller = new MerchantController(venueService, courtService, mock(SlotService.class));
  }

  @Test
  void 场馆接口覆盖创建编辑下架和本人列表() {
    MerchantVenueRequest request = new MerchantVenueRequest("场馆", "地址");
    Venue venue = new Venue();
    whenVenueCreate(request, venue);

    assertThat(controller.createVenue(request).getData()).isSameAs(venue);
    assertThat(controller.updateVenue(7L, request).getData()).isSameAs(venue);
    assertThat(controller.offlineVenue(7L).getCode()).isZero();
    assertThat(controller.myVenues().getData()).isEqualTo(List.of());
    verify(venueService).create(request);
    verify(venueService).update(7L, request);
    verify(venueService).offline(7L);
    verify(venueService).listMine();
  }

  @Test
  void 场地接口覆盖创建编辑下架和本人列表() {
    MerchantCourtRequest request =
        new MerchantCourtRequest(
            7L, "场地", "羽毛球", 1000, LocalTime.of(9, 0), LocalTime.of(21, 0), 15);
    Court court = new Court();
    whenCourtCreate(request, court);

    assertThat(controller.createCourt(request).getData()).isSameAs(court);
    assertThat(controller.updateCourt(8L, request).getData()).isSameAs(court);
    assertThat(controller.offlineCourt(8L).getCode()).isZero();
    assertThat(controller.myCourts().getData()).isEqualTo(List.of());
    verify(courtService).create(request);
    verify(courtService).update(8L, request);
    verify(courtService).offline(8L);
    verify(courtService).listMine();
  }

  private void whenVenueCreate(MerchantVenueRequest request, Venue venue) {
    org.mockito.Mockito.when(venueService.create(request)).thenReturn(venue);
    org.mockito.Mockito.when(venueService.update(7L, request)).thenReturn(venue);
    org.mockito.Mockito.when(venueService.listMine()).thenReturn(List.of());
  }

  private void whenCourtCreate(MerchantCourtRequest request, Court court) {
    org.mockito.Mockito.when(courtService.create(request)).thenReturn(court);
    org.mockito.Mockito.when(courtService.update(8L, request)).thenReturn(court);
    org.mockito.Mockito.when(courtService.listMine()).thenReturn(List.of());
  }
}
