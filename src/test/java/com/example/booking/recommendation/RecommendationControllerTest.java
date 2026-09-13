package com.example.booking.recommendation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.booking.config.AuthInterceptor;
import com.example.booking.config.WebMvcConfig;
import com.example.booking.common.GlobalExceptionHandler;
import com.example.booking.mapper.UserMapper;
import com.example.booking.service.TokenService;

@WebMvcTest(RecommendationController.class)
@Import({RecommendationController.class, WebMvcConfig.class, GlobalExceptionHandler.class})
@ContextConfiguration(classes = RecommendationControllerTest.TestApplication.class)
class RecommendationControllerTest {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestApplication {

    @Bean
    AuthInterceptor authInterceptor(TokenService tokenService, UserMapper userMapper) {
      return new AuthInterceptor(tokenService, userMapper);
    }
  }

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private SlotRecommendationService service;

  @MockBean private TokenService tokenService;

  @MockBean private UserMapper userMapper;

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
            post("/api/recommendations/slots")
                .contextPath("/api")
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

  @Test
  void recommend_非法日期返回参数不合法() throws Exception {
    mockMvc
        .perform(
            post("/api/recommendations/slots")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courtId\":101,\"date\":\"2026-02-30\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(4000))
        .andExpect(jsonPath("$.message").value("参数不合法"));
  }

  @Test
  void recommend_未知标签返回参数不合法() throws Exception {
    mockMvc
        .perform(
            post("/api/recommendations/slots")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courtId\":101,\"date\":\"2026-09-13\",\"tags\":[\"UNKNOWN\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(4000))
        .andExpect(jsonPath("$.message").value("参数不合法"));
  }
}
