package com.example.booking.service;

import com.example.booking.domain.dto.LoginRequest;
import com.example.booking.domain.dto.RegisterRequest;
import com.example.booking.domain.vo.LoginVO;
import com.example.booking.domain.vo.UserVO;

public interface AuthService {

  LoginVO register(RegisterRequest req);

  LoginVO login(LoginRequest req);

  void logout(String token);

  UserVO me();
}