package com.example.booking.recommendation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RecommendationControllerTest {

  private SlotRecommendationService service;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    service = mock(SlotRecommendationService.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new RecommendationController(service)).build();
  }

  @Test
  void recommend_通过只读入口返回规则推荐结果() throws Exception {
    when(service.recommend(any()))
        .thenReturn(
            new SlotRecommendationResponse(
                List.of(
                    new SlotRecommendationItem(
                        1L,
                        101L,
                        LocalDateTime.of(2026, 9, 13, 18, 0),
                        LocalDateTime.of(2026, 9, 13, 19, 0),
                        8000,
                        2,
                        102,
                        Set.of(RecommendationTag.EVENING),
                        "按时间与可约余量为你排序")),
                true));

    mockMvc
        .perform(
            post("/recommendations/slots")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courtId\":101,\"date\":\"2026-09-13\",\"tags\":[\"EVENING\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.degraded").value(true))
        .andExpect(jsonPath("$.data.recommendations[0].slotId").value(1))
        .andExpect(jsonPath("$.data.recommendations[0].available").value(2));

    ArgumentCaptor<SlotRecommendationRequest> captor =
        ArgumentCaptor.forClass(SlotRecommendationRequest.class);
    verify(service).recommend(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().courtId()).isEqualTo(101L);
    org.assertj.core.api.Assertions.assertThat(captor.getValue().tags())
        .containsExactly(RecommendationTag.EVENING);
  }
}
