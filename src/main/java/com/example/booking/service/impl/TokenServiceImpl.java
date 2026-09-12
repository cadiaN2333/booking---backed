package com.example.booking.service.impl;

import java.time.Duration;
import java.util.UUID;
import com.example.booking.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 会话令牌存取。
 *
 * <p>选型说明：这里用「随机 token + Redis 存储」而不是 JWT。
 * 取舍——
 * <ul>
 *   <li>JWT：无状态、不依赖 Redis，但无法主动失效（登出、封号、踢下线都要等过期），且不好续期。</li>
 *   <li>Redis 会话：可以主动 revoke、可以滑动续期、可以查到某用户全部在线会话，
 *       代价是每次请求多一次 Redis 查询（单机可忽略，集群用 Redis 本身也能扛）。</li>
 * </ul>
 * 预约类系统里「封号要立刻生效」是硬需求，所以选后者。
 */
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

  private static final String KEY_PREFIX = "auth:token:";
  private static final Duration TTL = Duration.ofDays(7);
  private static final Duration RENEW_THRESHOLD = Duration.ofDays(6);

  private final StringRedisTemplate redis;

  @Override
  public String issue(Long userId) {
    String token = UUID.randomUUID().toString().replace("-", "");
    redis.opsForValue().set(KEY_PREFIX + token, String.valueOf(userId), TTL);
    return token;
  }

  @Override
  public Long resolve(String token) {
    if (token == null || token.isBlank()) {
      return null;
    }
    String value = redis.opsForValue().get(KEY_PREFIX + token);
    return value == null ? null : Long.valueOf(value);
  }

  /** 滑动续期：剩余时间不足一半时自动延长，避免用户用着用着掉线 */
  @Override
  public void renewIfNeeded(String token) {
    String key = KEY_PREFIX + token;
    Long remain = redis.getExpire(key);
    if (remain != null && remain > 0 && remain < RENEW_THRESHOLD.getSeconds()) {
      redis.expire(key, TTL);
    }
  }

  @Override
  public void revoke(String token) {
    if (token != null && !token.isBlank()) {
      redis.delete(KEY_PREFIX + token);
    }
  }
}