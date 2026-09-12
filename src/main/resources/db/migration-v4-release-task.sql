-- 升级脚本：将预约超时释放从进程内队列升级为可恢复的持久化任务
USE `booking`;

CREATE TABLE IF NOT EXISTS `reservation_release_task` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no`    VARCHAR(32) NOT NULL,
  `execute_at`  DATETIME NOT NULL,
  `status`      TINYINT NOT NULL DEFAULT 0 COMMENT '0待执行 1处理中 2完成',
  `attempts`    INT NOT NULL DEFAULT 0,
  `last_error`  VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_release_order_no` (`order_no`),
  KEY `idx_release_due` (`status`, `execute_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约超时释放任务';
