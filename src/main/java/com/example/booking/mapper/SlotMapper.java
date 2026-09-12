package com.example.booking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.booking.domain.entity.Slot;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 库存变动全部走「单条带条件的 UPDATE」。
 *
 * <p>命中主键 -> InnoDB 加行锁，并发请求在同一行上自然串行；
 * 判断条件与扣减写在一条 SQL 里 -> 不存在 check-then-act 竞态。
 * 影响行数为 0 即代表条件不满足（已抢光 / 已无可释放）。
 *
 * <p>阶段二优化点：在 Redis 侧做 Lua 预扣挡住大部分流量，这里作为最终落库与对账依据。
 */
public interface SlotMapper extends BaseMapper<Slot> {

  /** 下单预扣：available -1, locked +1 */
  @Update(
      "UPDATE slot SET available = available - 1, locked = locked + 1, version = version + 1 "
          + "WHERE id = #{id} AND available > 0")
  int deductAvailable(@Param("id") Long id);

  /** 确认支付：locked -1, sold +1 */
  @Update(
      "UPDATE slot SET locked = locked - 1, sold = sold + 1, version = version + 1 "
          + "WHERE id = #{id} AND locked > 0")
  int confirmLocked(@Param("id") Long id);

  /** 取消 / 超时释放：locked -1, available +1 */
  @Update(
      "UPDATE slot SET locked = locked - 1, available = available + 1, version = version + 1 "
          + "WHERE id = #{id} AND locked > 0")
  int releaseLocked(@Param("id") Long id);

  /** 占用数调整：把历史的脏数据按守恒式修正（运维兜底用） */
  @Update(
      "UPDATE slot SET available = total - locked - sold WHERE id = #{id}")
  int rebuildAvailable(@Param("id") Long id);
}
