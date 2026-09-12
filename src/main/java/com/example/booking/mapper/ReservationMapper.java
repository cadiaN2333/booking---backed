package com.example.booking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.booking.domain.entity.Reservation;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ReservationMapper extends BaseMapper<Reservation> {

  /**
   * 状态机 CAS：只有当前状态等于 expect 才流转到 target。
   *
   * <p>「超时释放」与「用户支付」竞争同一行的行锁，
   * 两者都带 status=0 条件，因此只有一个能拿到影响行数 1 —— 这是不引入分布式锁就能解决该竞态的关键。
   */
  @Update(
      "UPDATE reservation SET status = #{target}, version = version + 1 "
          + "WHERE order_no = #{orderNo} AND status = #{expect}")
  int updateStatus(
      @Param("orderNo") String orderNo,
      @Param("expect") int expect,
      @Param("target") int target);

  @Select("SELECT * FROM reservation WHERE order_no = #{orderNo}")
  Reservation selectByOrderNo(@Param("orderNo") String orderNo);

  /** 补偿 Job 扫描：待支付且已过期。idx_status_expire 生效 */
  @Select(
      "SELECT * FROM reservation WHERE status = 0 AND expire_at < NOW() ORDER BY expire_at LIMIT #{limit}")
  List<Reservation> selectExpired(@Param("limit") int limit);

  /** 同一用户对同一时段的有效订单数，配合 uk_user_slot 唯一索引做双保险 */
  @Select(
      "SELECT COUNT(1) FROM reservation WHERE user_id = #{userId} AND slot_id = #{slotId} AND status IN (0, 1)")
  long countActive(@Param("userId") Long userId, @Param("slotId") Long slotId);

  /**
   * 被订单引用过的时段 id。
   *
   * <p>重新生成时段时必须避开这些行 —— 删掉它们会让订单 slot_id 悬空，
   * 更严重的是已售计数丢失，该时间段会被重复卖出。
   * 不区分订单状态，因为历史订单也要能看到对应的时段。
   */
  @Select("SELECT DISTINCT slot_id FROM reservation WHERE court_id = #{courtId}")
  List<Long> selectReferencedSlotIds(@Param("courtId") Long courtId);
}
