-- 升级脚本：加入场馆和场地的平台审核字段
-- 兼容已有库：首次增加审核字段时，历史资源迁移为审核通过且已上架；重复执行不会重置后续审核状态。
USE `booking`;

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
WHERE @venue_audit_status_exists = 0;

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
WHERE @court_audit_status_exists = 0;
