package com.example.booking.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.booking.common.BizException;
import com.example.booking.common.UserContext;
import com.example.booking.domain.entity.User;
import com.example.booking.mapper.UserMapper;
import com.example.booking.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AuthInterceptorTest {

  private final TokenService tokenService = mock(TokenService.class);
  private final UserMapper userMapper = mock(UserMapper.class);
  private final AuthInterceptor interceptor = new AuthInterceptor(tokenService, userMapper);
  private final HttpServletResponse response = mock(HttpServletResponse.class);

  @AfterEach
  void clearUserContext() {
    UserContext.clear();
  }

  @Test
  void 顾客访问管理员接口返回4030() {
    assertThatThrownBy(() -> invoke("/admin/reviews", 0))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 4030);
  }

  @Test
  void 商家访问管理员接口返回4030() {
    assertThatThrownBy(() -> invoke("/admin/reviews", 1))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 4030);
  }

  @Test
  void 管理员访问商家接口返回4030() {
    assertThatThrownBy(() -> invoke("/merchant/venues", 2))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 4030);
  }

  @Test
  void 管理员访问管理员接口并写入用户上下文() {
    HttpServletRequest request = request("/admin/reviews");
    User user = user(2);
    when(tokenService.resolve("token")).thenReturn(9L);
    when(userMapper.selectById(9L)).thenReturn(user);

    assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    assertThat(UserContext.role()).isEqualTo(2);
    interceptor.afterCompletion(request, response, new Object(), null);
    assertThat(UserContext.get()).isNull();
  }

  @Test
  void 商家访问商家接口继续放行() {
    assertThatCode(() -> invoke("/merchant/venues", 1)).doesNotThrowAnyException();
  }

  private void invoke(String path, int role) {
    HttpServletRequest request = request(path);
    when(tokenService.resolve("token")).thenReturn(9L);
    when(userMapper.selectById(9L)).thenReturn(user(role));
    interceptor.preHandle(request, response, new Object());
  }

  private HttpServletRequest request(String path) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getMethod()).thenReturn("GET");
    when(request.getHeader("Authorization")).thenReturn("Bearer token");
    when(request.getServletPath()).thenReturn(path);
    return request;
  }

  private User user(int role) {
    User user = new User();
    user.setId(9L);
    user.setUsername("user");
    user.setNickname("用户");
    user.setRole(role);
    user.setStatus(1);
    return user;
  }
}
