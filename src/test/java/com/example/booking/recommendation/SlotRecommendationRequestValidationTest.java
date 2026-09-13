package com.example.booking.recommendation;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.booking.common.GlobalExceptionHandler;
import com.example.booking.config.AuthInterceptor;
import com.example.booking.config.WebMvcConfig;
import com.example.booking.mapper.UserMapper;
import com.example.booking.service.SlotService;
import com.example.booking.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RecommendationController.class)
@Import({
  RecommendationController.class,
  SlotRecommendationService.class,
  WebMvcConfig.class,
  GlobalExceptionHandler.class
})
@ContextConfiguration(classes = SlotRecommendationRequestValidationTest.TestApplication.class)
class SlotRecommendationRequestValidationTest {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestApplication {

    @Bean
    AuthInterceptor authInterceptor(TokenService tokenService, UserMapper userMapper) {
      return new AuthInterceptor(tokenService, userMapper);
    }
  }

  @Autowired private MockMvc mockMvc;

  @MockBean private SlotService slotService;

  @MockBean private RecommendationSemanticService semanticService;

  @MockBean private TokenService tokenService;

  @MockBean private UserMapper userMapper;

  @Test
  void recommend_标签集合含空元素时返回参数不合法() throws Exception {
    mockMvc
        .perform(
            post("/api/recommendations/slots")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courtId\":101,\"date\":\"2026-09-13\",\"tags\":[null]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code", is(4000)))
        .andExpect(jsonPath("$.message", is("参数不合法")));
  }
}
