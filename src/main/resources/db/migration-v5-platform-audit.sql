-- 升级脚本：加入场馆和场地的平台审核字段
-- 兼容已有库：首次增加审核字段时，历史资源迁移为审核通过且已上架；重复执行不会重置后续审核状态。
USE `booking`;

-- 独立完成标记：只有两张表历史数据都回填成功后才写入，便于中断后重跑继续回填。
CREATE TABLE IF NOT EXISTS `platform_migration` (
  `version` VARCHAR(64) NOT NULL,
  `completed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台迁移完成标记';

SET @platform_audit_v5_completed := (
  SELECT COUNT(*)
  FROM `platform_migration`
  WHERE `version` = 'v5-platform-audit'
);

-- 新建审核资源默认下架；MODIFY COLUMN 可重复执行，且不改动已有行数据。
ALTER TABLE `venue`
  MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0 COMMENT '1已上架 0下架';

ALTER TABLE `court`
  MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0 COMMENT '1已上架 0下架';

SET @venue_audit_status_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'venue'
    AND column_name = 'audit_status'
);
SET @sql := IF(
  @venue_audit_status_exists = 0,
  'ALTER TABLE `venue` ADD COLUMN `audit_status` TINYINT NOT NULL DEFAULT 0 COMMENT ''0待审核 1审核通过 2已驳回'' AFTER `status`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'venue'
      AND column_name = 'audit_remark'
  ),
  'ALTER TABLE `venue` ADD COLUMN `audit_remark` VARCHAR(500) DEFAULT NULL COMMENT ''审核备注或驳回原因'' AFTER `audit_status`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'venue'
      AND column_name = 'audit_time'
  ),
  'ALTER TABLE `venue` ADD COLUMN `audit_time` DATETIME DEFAULT NULL COMMENT ''审核时间'' AFTER `audit_remark`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'venue'
      AND column_name = 'audit_by'
  ),
  'ALTER TABLE `venue` ADD COLUMN `audit_by` BIGINT UNSIGNED DEFAULT NULL COMMENT ''审核人 user.id'' AFTER `audit_time`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `venue`
SET `audit_status` = 1,
    `status` = 1
WHERE @platform_audit_v5_completed = 0;

SET @court_audit_status_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'court'
    AND column_name = 'audit_status'
);
SET @sql := IF(
  @court_audit_status_exists = 0,
  'ALTER TABLE `court` ADD COLUMN `audit_status` TINYINT NOT NULL DEFAULT 0 COMMENT ''0待审核 1审核通过 2已驳回'' AFTER `status`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'court'
      AND column_name = 'audit_remark'
  ),
  'ALTER TABLE `court` ADD COLUMN `audit_remark` VARCHAR(500) DEFAULT NULL COMMENT ''审核备注或驳回原因'' AFTER `audit_status`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'court'
      AND column_name = 'audit_time'
  ),
  'ALTER TABLE `court` ADD COLUMN `audit_time` DATETIME DEFAULT NULL COMMENT ''审核时间'' AFTER `audit_remark`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'court'
      AND column_name = 'audit_by'
  ),
  'ALTER TABLE `court` ADD COLUMN `audit_by` BIGINT UNSIGNED DEFAULT NULL COMMENT ''审核人 user.id'' AFTER `audit_time`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `court`
SET `audit_status` = 1,
    `status` = 1
WHERE @platform_audit_v5_completed = 0;

-- 只有历史回填完成后才记录版本；前面任一步中断时重跑仍会继续回填。
INSERT INTO `platform_migration` (`version`)
SELECT 'v5-platform-audit'
FROM DUAL
WHERE @platform_audit_v5_completed = 0
ON DUPLICATE KEY UPDATE `version` = VALUES(`version`);
