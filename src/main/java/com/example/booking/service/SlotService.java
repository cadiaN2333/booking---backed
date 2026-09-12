package com.example.booking.service;

import com.example.booking.domain.vo.SlotVO;
import java.time.LocalDate;
import java.util.List;

public interface SlotService {

  /** 某场地某天的时段列表。阶段二优化点：这里加 Redis 缓存 + 逻辑过期防击穿 */
  List<SlotVO> listByCourtAndDate(Long courtId, LocalDate date);

  /**
   * 商家端：为当前登录商家名下的场地生成时段。
   *
   * <p>天数取配置 booking.generate-days。归属校验放在这里，Controller 不需要知道。
   */
  int generateForMerchant(Long courtId);

  /** 为单个场地生成未来 days 天的时段 */
  int generate(Long courtId, int days);

  /** 为所有场地生成未来 days 天的时段 */
  int generateAll(int days);
}
