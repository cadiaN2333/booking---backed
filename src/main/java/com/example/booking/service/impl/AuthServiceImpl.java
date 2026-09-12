package com.example.booking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.booking.common.BizException;
import com.example.booking.common.UserContext;
import com.example.booking.domain.dto.LoginRequest;
import com.example.booking.domain.dto.RegisterRequest;
import com.example.booking.domain.entity.User;
import com.example.booking.domain.enums.UserRoleEnum;
import com.example.booking.domain.vo.LoginVO;
import com.example.booking.domain.vo.UserVO;
import com.example.booking.mapper.UserMapper;
import com.example.booking.service.AuthService;
import com.example.booking.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private final UserMapper userMapper;
  private final TokenService tokenService;
  private final PasswordEncoder passwordEncoder;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public LoginVO register(RegisterRequest req) {
    Long exists =
        userMapper.selectCount(
            new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
    if (exists != null && exists > 0) {
      throw new BizException("用户名已被占用");
    }

    // 允许直接选商家仅为演示方便；真实项目商家入驻应走资质审核 + 线下签约
    int role = UserRoleEnum.isMerchant(req.getRole())
        ? UserRoleEnum.MERCHANT.getCode()
        : UserRoleEnum.CUSTOMER.getCode();

    User user = new User();
    user.setUsername(req.getUsername());
    user.setPassword(passwordEncoder.encode(req.getPassword()));
    user.setNickname(req.getNickname());
    user.setPhone(req.getPhone());
    user.setRole(role);
    user.setStatus(1);
    userMapper.insert(user);

    // 注册即登录，省掉一次跳转
    String token = tokenService.issue(user.getId());
    log.info("新用户注册 id={} username={} role={}", user.getId(), user.getUsername(), role);
    return new LoginVO(token, UserVO.from(user));
  }

  @Override
  public LoginVO login(LoginRequest req) {
    User user =
        userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
    // 用户名不存在与密码错误返回同一提示，避免被用来枚举账号
    if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
      throw new BizException("用户名或密码错误");
    }
    if (user.getStatus() == null || user.getStatus() != 1) {
      throw new BizException("账号已被禁用");
    }

    String token = tokenService.issue(user.getId());
    log.info("用户登录 id={} username={}", user.getId(), user.getUsername());
    return new LoginVO(token, UserVO.from(user));
  }

  @Override
  public void logout(String token) {
    tokenService.revoke(token);
  }

  @Override
  public UserVO me() {
    User user = userMapper.selectById(UserContext.userId());
    if (user == null) {
      throw new BizException(4011, "登录已过期，请重新登录");
    }
    return UserVO.from(user);
  }
}