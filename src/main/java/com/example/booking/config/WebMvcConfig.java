package com.example.booking.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

  private final AuthInterceptor authInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(authInterceptor)
        .addPathPatterns("/**")
        .excludePathPatterns(
            // 登录注册开放
            "/auth/login",
            "/auth/register",
            // 浏览场馆与时段不需要登录，下单才需要
            "/venues",
            "/courts",
            "/courts/*/slots",
            "/error");
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        // 用 [*] 放行本机任意端口。
        // 写死 5173 会在 Vite 端口被占用自动退到 5174 时，让带 Origin 的 POST 收到 403。
        // 注意：allowCredentials(true) 时不能用 allowedOrigins("*")，
        // 必须用 allowedOriginPatterns 才能匹配端口通配。
        .allowedOriginPatterns("http://localhost:[*]", "http://127.0.0.1:[*]")
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        // 让前端能读到自定义响应头
        .exposedHeaders("Authorization")
        .allowCredentials(true)
        .maxAge(3600);
  }
}
