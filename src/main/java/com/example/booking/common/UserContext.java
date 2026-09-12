package com.example.booking.common;

/**
 * 当前登录用户上下文。
 *
 * <p>由 {@code AuthInterceptor} 在请求进入时写入、请求结束时清理，
 * 业务层通过 {@link #userId()} 取用，避免在每个方法签名上透传 userId。
 *
 * <p>注意：ThreadLocal 必须在 afterCompletion 里 remove，否则线程池复用会造成用户串号。
 */
public final class UserContext {

  private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

  private UserContext() {}

  public static void set(LoginUser user) {
    HOLDER.set(user);
  }

  public static LoginUser get() {
    return HOLDER.get();
  }

  public static void clear() {
    HOLDER.remove();
  }

  public static Long userId() {
    LoginUser u = HOLDER.get();
    if (u == null) {
      throw new BizException(4010, "请先登录");
    }
    return u.id();
  }

  public static Integer role() {
    LoginUser u = HOLDER.get();
    return u == null ? null : u.role();
  }

  public record LoginUser(Long id, String username, String nickname, Integer role) {}
}
