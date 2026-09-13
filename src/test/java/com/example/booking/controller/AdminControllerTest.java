package com.example.booking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.booking.common.GlobalExceptionHandler;
import com.example.booking.domain.vo.AdminReviewVO;
import com.example.booking.service.AdminAuditService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
@Import({AdminController.class, GlobalExceptionHandler.class})
@ContextConfiguration(classes = AdminControllerTest.TestApplication.class)
class AdminControllerTest {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestApplication {}

  @Autowired private MockMvc mockMvc;

  @MockBean private AdminAuditService service;

  @Test
  void 查询审核列表路由调用服务并返回视图() throws Exception {
    when(service.listReviews("court", 0)).thenReturn(List.of(new AdminReviewVO()));

    mockMvc
        .perform(get("/api/admin/reviews").contextPath("/api").param("type", "court"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data").isArray());

    verify(service).listReviews("court", 0);
  }

  @Test
  void 审核状态超出范围时返回参数错误且不调用服务() throws Exception {
    mockMvc
        .perform(get("/api/admin/reviews").contextPath("/api").param("status", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(4000))
        .andExpect(jsonPath("$.message").value("审核状态必须是0、1或2"));

    org.mockito.Mockito.verifyNoInteractions(service);
  }

  @Test
  void 审核状态非数字时返回参数错误且不调用服务() throws Exception {
    mockMvc
        .perform(get("/api/admin/reviews").contextPath("/api").param("status", "abc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(4000))
        .andExpect(jsonPath("$.message").value("审核状态必须是0、1或2"));

    org.mockito.Mockito.verifyNoInteractions(service);
  }

  @Test
  void 通过和驳回路由分别调用对应服务() throws Exception {
    mockMvc
        .perform(post("/api/admin/reviews/venues/201/approve").contextPath("/api"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));
    mockMvc
        .perform(
            post("/api/admin/reviews/courts/101/reject")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"资料不完整\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));

    verify(service).approveVenue(201L);
    verify(service).rejectCourt(101L, "资料不完整");
  }

  @Test
  void 驳回原因为空时由参数校验返回中文提示且不调用服务() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/reviews/venues/201/reject")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\" \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(4000))
        .andExpect(jsonPath("$.message").value("驳回原因不能为空"));

    org.mockito.Mockito.verifyNoInteractions(service);
  }

  @Test
  void 驳回原因超过五百字时由参数校验拒绝() throws Exception {
    String reason = "a".repeat(501);

    mockMvc
        .perform(
            post("/api/admin/reviews/venues/201/reject")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + reason + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(4000))
        .andExpect(jsonPath("$.message").value("驳回原因不能超过500个字符"));
  }
}
