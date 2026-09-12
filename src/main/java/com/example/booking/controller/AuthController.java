package com.example.booking.controller;

import com.example.booking.common.Result;
import com.example.booking.domain.dto.LoginRequest;
import com.example.booking.domain.dto.RegisterRequest;
import com.example.booking.domain.vo.LoginVO;
import com.example.booking.domain.vo.UserVO;
import com.example.booking.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/register")
  public Result<LoginVO> register(@Valid @RequestBody RegisterRequest request) {
    return Result.ok(authService.register(request));
  }

  @PostMapping("/login")
  public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
    return Result.ok(authService.login(request));
  }

  @PostMapping("/logout")
  public Result<Boolean> logout(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    String token = null;
    if (authorization != null && authorization.startsWith("Bearer ")) {
      token = authorization.substring(7).trim();
    }
    authService.logout(token);
    return Result.ok(true);
  }

  @GetMapping("/me")
  public Result<UserVO> me() {
    return Result.ok(authService.me());
  }
}
