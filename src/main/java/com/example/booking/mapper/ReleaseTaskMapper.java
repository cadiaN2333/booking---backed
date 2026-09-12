package com.example.booking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.booking.domain.entity.ReleaseTask;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 预约释放任务的持久化队列操作。 */
public interface ReleaseTaskMapper extends BaseMapper<ReleaseTask> {

  @Select(
      "SELECT * FROM reservation_release_task "
          + "WHERE status = 0 AND execute_at <= NOW() "
          + "ORDER BY execute_at LIMIT #{limit}")
  List<ReleaseTask> selectDue(@Param("limit") int limit);

  /** 只有一个实例可以把待执行任务抢到处理中。 */
  @Update(
      "UPDATE reservation_release_task SET status = 1, attempts = attempts + 1, "
          + "update_time = NOW() WHERE id = #{id} AND status = 0 AND execute_at <= NOW()")
  int claim(@Param("id") Long id);

  @Update(
      "UPDATE reservation_release_task SET status = 2, last_error = NULL, update_time = NOW() "
          + "WHERE id = #{id} AND status = 1")
  int markDone(@Param("id") Long id);

  /** 订单已确认或取消时，提前结束尚未到期的释放任务。 */
  @Update(
      "UPDATE reservation_release_task SET status = 2, last_error = NULL, update_time = NOW() "
          + "WHERE order_no = #{orderNo} AND status IN (0, 1)")
  int finishByOrderNo(@Param("orderNo") String orderNo);

  @Update(
      "UPDATE reservation_release_task SET status = 0, execute_at = #{executeAt}, "
          + "last_error = #{lastError}, update_time = NOW() "
          + "WHERE id = #{id} AND status = 1")
  int retry(
      @Param("id") Long id,
      @Param("executeAt") LocalDateTime executeAt,
      @Param("lastError") String lastError);

  /** 进程崩溃留下的处理中任务在两分钟后重新进入队列。 */
  @Update(
      "UPDATE reservation_release_task SET status = 0, update_time = NOW() "
          + "WHERE status = 1 AND update_time < DATE_SUB(NOW(), INTERVAL 2 MINUTE)")
  int recoverStuck();
}
