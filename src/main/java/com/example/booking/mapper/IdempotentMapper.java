package com.example.booking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.booking.domain.entity.IdempotentRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface IdempotentMapper extends BaseMapper<IdempotentRecord> {

  /**
   * 消费令牌：仅当 result 为 NULL 时才能抢占成功。
   * 影响行数 0 = 该令牌已被消费过，即重复提交。
   */
  @Update(
      "UPDATE idempotent_record SET result = 'PROCESSING' "
          + "WHERE token = #{token} AND user_id = #{userId} AND biz_type = #{bizType} AND result IS NULL")
  int consume(
      @Param("token") String token,
      @Param("userId") Long userId,
      @Param("bizType") String bizType);

  /** 写回首次执行结果，重复提交时可直接返回该订单号 */
  @Update("UPDATE idempotent_record SET result = #{orderNo} WHERE token = #{token}")
  int finish(@Param("token") String token, @Param("orderNo") String orderNo);

  /** 业务失败时把令牌重置，允许用户重试 */
  @Update("UPDATE idempotent_record SET result = NULL WHERE token = #{token}")
  int reset(@Param("token") String token);
}
