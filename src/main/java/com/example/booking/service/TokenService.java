package com.example.booking.service;

public interface TokenService {

  String issue(Long userId);

  Long resolve(String token);

  void renewIfNeeded(String token);

  void revoke(String token);
}