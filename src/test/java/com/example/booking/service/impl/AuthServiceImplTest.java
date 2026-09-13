package com.example.booking.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.example.booking.domain.dto.RegisterRequest;
import com.example.booking.domain.entity.User;
import com.example.booking.common.BizException;
import com.example.booking.mapper.UserMapper;
import com.example.booking.service.TokenService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceImplTest {

  @Test
  void 普通注册顾客和商家分别保存对应角色() {
    assertThatRegisteredRoleIs(0, 0);
    assertThatRegisteredRoleIs(1, 1);
  }

  @Test
  void 普通注册传入管理员角色不得创建管理员() {
    UserMapper userMapper = mock(UserMapper.class);
    TokenService tokenService = mock(TokenService.class);
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    when(userMapper.selectCount(any())).thenReturn(0L);
    when(passwordEncoder.encode("password")).thenReturn("encoded");
    when(tokenService.issue(any())).thenReturn("token");

    RegisterRequest request = new RegisterRequest();
    request.setUsername("new-user");
    request.setPassword("password");
    request.setNickname("新用户");
    request.setRole(2);

    assertThatThrownBy(
            () -> new AuthServiceImpl(userMapper, tokenService, passwordEncoder).register(request))
        .isInstanceOf(BizException.class)
        .hasMessage("注册角色不合法");

    verify(userMapper, never()).insert(any(User.class));
  }

  private void assertThatRegisteredRoleIs(int requestedRole, int expectedRole) {
    UserMapper userMapper = mock(UserMapper.class);
    TokenService tokenService = mock(TokenService.class);
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    when(userMapper.selectCount(any())).thenReturn(0L);
    when(passwordEncoder.encode("password")).thenReturn("encoded");
    when(tokenService.issue(any())).thenReturn("token");

    RegisterRequest request = new RegisterRequest();
    request.setUsername("user-" + requestedRole);
    request.setPassword("password");
    request.setNickname("用户");
    request.setRole(requestedRole);

    new AuthServiceImpl(userMapper, tokenService, passwordEncoder).register(request);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userMapper).insert(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().getRole()).isEqualTo(expectedRole);
  }
}
