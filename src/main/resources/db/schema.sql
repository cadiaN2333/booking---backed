-- 场地预约排期系统 · 建表脚本
CREATE DATABASE IF NOT EXISTS `booking` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `booking`;

-- 用户：role 0=顾客 1=商家 2=管理员
CREATE TABLE IF NOT EXISTS `user` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `username`    VARCHAR(32)  NOT NULL COMMENT '登录名',
  `password`    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
  `nickname`    VARCHAR(32)  NOT NULL,
  `phone`       VARCHAR(20)  DEFAULT NULL,
  `role`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0顾客 1商家 2管理员',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- 场馆
CREATE TABLE IF NOT EXISTS `venue` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(64)  NOT NULL,
  `address`     VARCHAR(255) DEFAULT NULL,
  `merchant_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '归属商家 user.id',
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '1已上架 0下架',
  `audit_status` TINYINT     NOT NULL DEFAULT 0 COMMENT '0待审核 1审核通过 2已驳回',
  `audit_remark` VARCHAR(500) DEFAULT NULL COMMENT '审核备注或驳回原因',
  `audit_time` DATETIME     DEFAULT NULL COMMENT '审核时间',
  `audit_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '审核人 user.id',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_merchant` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场馆';

-- 场地单元（一个场馆下有多个可预约的场地）
CREATE TABLE IF NOT EXISTS `court` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `venue_id`     BIGINT UNSIGNED NOT NULL COMMENT '所属场馆',
  `name`         VARCHAR(64)  NOT NULL,
  `type`         VARCHAR(32)  NOT NULL COMMENT '羽毛球/会议室/自习室',
  `price`        INT          NOT NULL COMMENT '每时段价格(分)',
  `open_time`    TIME         NOT NULL,
  `close_time`   TIME         NOT NULL,
  `slot_minutes` INT          NOT NULL DEFAULT 60 COMMENT '单时段时长(分钟)',
  `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '1已上架 0下架',
  `audit_status` TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审核 1审核通过 2已驳回',
  `audit_remark` VARCHAR(500) DEFAULT NULL COMMENT '审核备注或驳回原因',
  `audit_time` DATETIME       DEFAULT NULL COMMENT '审核时间',
  `audit_by` BIGINT UNSIGNED  DEFAULT NULL COMMENT '审核人 user.id',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_venue` (`venue_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场地单元';

-- 时段库存（核心表）
-- 库存三段式：available + locked + sold = total，由对账任务校验
CREATE TABLE IF NOT EXISTS `slot` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `court_id`    BIGINT UNSIGNED NOT NULL,
  `biz_date`    DATE     NOT NULL COMMENT '营业日',
  `start_at`    DATETIME NOT NULL COMMENT '时段开始',
  `end_at`      DATETIME NOT NULL COMMENT '时段结束',
  `total`       INT      NOT NULL DEFAULT 1  COMMENT '总库存',
  `available`   INT      NOT NULL DEFAULT 1  COMMENT '可约库存',
  `locked`      INT      NOT NULL DEFAULT 0  COMMENT '锁定中(待支付)',
  `sold`        INT      NOT NULL DEFAULT 0  COMMENT '已售',
  `version`     INT      NOT NULL DEFAULT 0  COMMENT '版本号',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_court_start` (`court_id`, `start_at`),
  KEY `idx_date_court` (`biz_date`, `court_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='时段库存';

-- 预约单
CREATE TABLE IF NOT EXISTS `reservation` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no`    VARCHAR(32) NOT NULL COMMENT '订单号',
  `user_id`     BIGINT UNSIGNED NOT NULL,
  `court_id`    BIGINT UNSIGNED NOT NULL,
  `slot_id`     BIGINT UNSIGNED NOT NULL,
  `active_slot_id` BIGINT UNSIGNED GENERATED ALWAYS AS (
    CASE WHEN `status` IN (0, 1) THEN `slot_id` ELSE NULL END
  ) STORED COMMENT '仅有效订单参与唯一约束，取消/超时后允许再次预约',
  `biz_date`    DATE     NOT NULL,
  `start_at`    DATETIME NOT NULL,
  `end_at`      DATETIME NOT NULL,
  `amount`      INT      NOT NULL COMMENT '金额(分)',
  `status`      TINYINT  NOT NULL DEFAULT 0 COMMENT '0待支付 1已确认 2已取消 3已超时 4已完成',
  `expire_at`   DATETIME NOT NULL COMMENT '锁定过期时间',
  `version`     INT      NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_user_active_slot` (`user_id`, `active_slot_id`) COMMENT '有效订单防重复预约',
  KEY `idx_status_expire` (`status`, `expire_at`),
  KEY `idx_user` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约单';

-- 持久化超时释放任务：下单事务与预约单一起写入，重启后可恢复
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

-- 幂等表：token + biz_type 唯一，拦截按钮连点与网关重试
CREATE TABLE IF NOT EXISTS `idempotent_record` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `token`       VARCHAR(64) NOT NULL,
  `user_id`     BIGINT UNSIGNED NOT NULL,
  `biz_type`    VARCHAR(32) NOT NULL,
  `result`      VARCHAR(64) DEFAULT NULL COMMENT '首次执行结果(orderNo)，NULL 表示未消费',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token` (`token`, `biz_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='幂等记录';
