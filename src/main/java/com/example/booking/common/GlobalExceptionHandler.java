package com.example.booking.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BizException.class)
  public Result<Void> handleBiz(BizException e) {
    return Result.fail(e.getCode(), e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public Result<Void> handleValid(MethodArgumentNotValidException e) {
    String msg = e.getBindingResult().getFieldError() == null
        ? "参数不合法"
        : e.getBindingResult().getFieldError().getDefaultMessage();
    return Result.fail(4000, msg);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public Result<Void> handleUnreadable(HttpMessageNotReadableException e) {
    return Result.fail(4000, "参数不合法");
  }

  /** 唯一索引冲突兜底：并发下幂等防线被击穿时给出可读提示 */
  @ExceptionHandler(DuplicateKeyException.class)
  public Result<Void> handleDuplicate(DuplicateKeyException e) {
    log.warn("唯一索引冲突", e);
    return Result.fail("请勿重复提交");
  }

  @ExceptionHandler(Exception.class)
  public Result<Void> handleOther(Exception e) {
    log.error("未处理异常", e);
    return Result.fail(5000, "服务异常，请稍后重试");
  }
}
