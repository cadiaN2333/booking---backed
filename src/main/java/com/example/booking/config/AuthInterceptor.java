package com.example.booking.config;

import com.example.booking.common.BizException;
import com.example.booking.common.UserContext;
import com.example.booking.domain.entity.User;
import com.example.booking.domain.enums.UserRoleEnum;
import com.example.booking.mapper.UserMapper;
import com.example.booking.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

  private static final String BEARER = "Bearer ";
  private static final String MERCHANT_PREFIX = "/merchant";

  private final TokenService tokenService;
  private final UserMapper userMapper;

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    // 预检请求直接放行，否则 CORS 会失败
    if (HttpMethod.OPTIONS.matches(request.getMethod())) {
      return true;
    }

    String token = extractToken(request);
    if (token == null) {
      throw new BizException(4010, "请先登录");
    }

    Long userId = tokenService.resolve(token);
    if (userId == null) {
      throw new BizException(4011, "登录已过期，请重新登录");
    }

    User user = userMapper.selectById(userId);
    if (user == null || user.getStatus() == null || user.getStatus() != 1) {
      throw new BizException(4012, "账号不可用");
    }

    // 商家端接口做角色校验：顾客访问一律 403
    String path = request.getServletPath();
    if (path.startsWith(MERCHANT_PREFIX) && !UserRoleEnum.isMerchant(user.getRole())) {
      throw new BizException(4030, "无商家权限");
    }

    UserContext.set(
        new UserContext.LoginUser(user.getId(), user.getUsername(), user.getNickname(), user.getRole()));
    tokenService.renewIfNeeded(token);
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    UserContext.clear();
  }

  private String extractToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith(BEARER)) {
      return header.substring(BEARER.length()).trim();
    }
    return null;
  }
}
