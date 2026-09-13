package com.example.booking.recommendation;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.booking.common.GlobalExceptionHandler;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GlobalRecommendationController.class)
@Import({GlobalRecommendationController.class, GlobalExceptionHandler.class})
@ContextConfiguration(classes = GlobalRecommendationControllerTest.TestApplication.class)
class GlobalRecommendationControllerTest {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestApplication {}

  @Autowired private MockMvc mockMvc;

  @MockBean private GlobalRecommendationService service;

  @Test
  void search_返回全局推荐场馆场地时段和库存() throws Exception {
    when(service.search(any()))
        .thenReturn(
            new GlobalRecommendationResponse(
                List.of(
                    new GlobalRecommendationItem(
                        1L,
                        "星辰羽毛球馆",
                        "天河区体育西路 88 号",
                        101L,
                        "1 号场",
                        "羽毛球",
                        11L,
                        LocalDate.of(2026, 9, 13),
                        LocalDateTime.of(2026, 9, 13, 18, 0),
                        LocalDateTime.of(2026, 9, 13, 19, 0),
                        8000,
                        2,
                        102,
                        Set.of(RecommendationTag.EVENING),
                        "匹配晚间可约时段")),
                true));

    mockMvc
        .perform(
            post("/api/recommendations/search")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"query\":\"明天晚上\",\"date\":\"2026-09-13\",\"type\":\"羽毛球\",\"maxPrice\":10000,\"limit\":5}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code", is(0)))
        .andExpect(jsonPath("$.data.degraded", is(true)))
        .andExpect(jsonPath("$.data.recommendations[0].venueName", is("星辰羽毛球馆")))
        .andExpect(jsonPath("$.data.recommendations[0].venueAddress", is("天河区体育西路 88 号")))
        .andExpect(jsonPath("$.data.recommendations[0].courtId", is(101)))
        .andExpect(jsonPath("$.data.recommendations[0].courtType", is("羽毛球")))
        .andExpect(jsonPath("$.data.recommendations[0].price", is(8000)))
        .andExpect(jsonPath("$.data.recommendations[0].available", is(2)))
        .andExpect(jsonPath("$.data.recommendations[0].score", is(102)))
        .andExpect(jsonPath("$.data.recommendations[0].reason", is("匹配晚间可约时段")));

    ArgumentCaptor<GlobalRecommendationRequest> captor =
        ArgumentCaptor.forClass(GlobalRecommendationRequest.class);
    verify(service).search(captor.capture());
    GlobalRecommendationRequest request = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(request.query()).isEqualTo("明天晚上");
    org.assertj.core.api.Assertions.assertThat(request.type()).isEqualTo("羽毛球");
    org.assertj.core.api.Assertions.assertThat(request.maxPrice()).isEqualTo(10000);
    org.assertj.core.api.Assertions.assertThat(request.limit()).isEqualTo(5);
  }

  @Test
  void search_非法日期返回参数不合法() throws Exception {
    mockMvc
        .perform(
            post("/api/recommendations/search")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"测试\",\"date\":\"2026-02-30\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code", is(4000)))
        .andExpect(jsonPath("$.message", is("参数不合法")));
  }

  @Test
  void search_超出返回数量上限返回参数不合法() throws Exception {
    mockMvc
        .perform(
            post("/api/recommendations/search")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"limit\":6}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code", is(4000)))
        .andExpect(jsonPath("$.message", is("limit 最大为 5")));
  }
}
