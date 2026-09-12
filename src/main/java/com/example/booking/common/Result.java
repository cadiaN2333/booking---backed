package com.example.booking.common;

import java.io.Serializable;
import lombok.Data;

/** 统一返回体，与前端 ApiResult<T> 对齐 */
@Data
public class Result<T> implements Serializable {

  private static final long serialVersionUID = 1L;

  private int code;
  private String message;
  private T data;

  public static <T> Result<T> ok(T data) {
    Result<T> r = new Result<>();
    r.setCode(0);
    r.setMessage("ok");
    r.setData(data);
    return r;
  }

  public static <T> Result<T> ok() {
    return ok(null);
  }

  public static <T> Result<T> fail(int code, String message) {
    Result<T> r = new Result<>();
    r.setCode(code);
    r.setMessage(message);
    return r;
  }

  public static <T> Result<T> fail(String message) {
    return fail(4001, message);
  }
}
