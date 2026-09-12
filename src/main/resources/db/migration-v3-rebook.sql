-- 升级脚本：允许用户取消或超时后再次预约同一时段
-- 历史订单仍保留；仅让待确认和已确认订单参与唯一约束。
USE `booking`;

ALTER TABLE `reservation`
  DROP INDEX `uk_user_slot`,
  ADD COLUMN `active_slot_id` BIGINT UNSIGNED GENERATED ALWAYS AS (
    CASE WHEN `status` IN (0, 1) THEN `slot_id` ELSE NULL END
  ) STORED COMMENT '仅有效订单参与唯一约束' AFTER `slot_id`,
  ADD UNIQUE KEY `uk_user_active_slot` (`user_id`, `active_slot_id`)
    COMMENT '有效订单防重复预约';
