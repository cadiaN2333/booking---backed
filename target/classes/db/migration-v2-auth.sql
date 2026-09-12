-- 升级脚本：加入用户体系与商家归属
-- 已建过库的环境执行这个；全新环境直接跑 schema.sql 即可（已包含以下内容）
USE `booking`;

-- 用户表：role 0=顾客 1=商家
CREATE TABLE IF NOT EXISTS `user` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `username`    VARCHAR(32)  NOT NULL COMMENT '登录名',
  `password`    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
  `nickname`    VARCHAR(32)  NOT NULL,
  `phone`       VARCHAR(20)  DEFAULT NULL,
  `role`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0顾客 1商家',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- 场馆归属到商家（幂等：重复执行会报字段已存在，忽略即可）
ALTER TABLE `venue`
  ADD COLUMN `merchant_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '归属商家 user.id' AFTER `address`,
  ADD KEY `idx_merchant` (`merchant_id`);
