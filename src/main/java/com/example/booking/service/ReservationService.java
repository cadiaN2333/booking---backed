package com.example.booking.service;

import com.example.booking.domain.dto.CreateReservationRequest;
import com.example.booking.domain.vo.ReservationVO;
import java.util.List;

public interface ReservationService {

  /** 下发幂等令牌，进入确认页时调用。userId 取当前登录态 */
  String createToken(Long slotId);

  /** 下单：幂等 + 原子扣库存 + 建单 + 预约超时释放 */
  ReservationVO create(CreateReservationRequest request);

  void confirm(String orderNo);

  void cancel(String orderNo);

  /**
   * 超时释放，幂等。
   *
   * @return true 表示本次真正释放成功；false 表示订单已被确认 / 取消 / 释放过，直接跳过
   */
  boolean release(String orderNo);

  /** 当前登录用户的预约列表 */
  List<ReservationVO> listMine();

  ReservationVO getByOrderNo(String orderNo);
}
