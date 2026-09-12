package com.example.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 只引入 spring-security-crypto 的加密能力，不启用整套 Security 过滤链 */
@Configuration
public class SecurityBeansConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    // BCrypt：自带随机盐，同一明文每次密文都不同，不能用 equals 比对，只能用 matches
    return new BCryptPasswordEncoder();
  }
}
