USE `booking`;

INSERT INTO `venue` (`id`, `name`, `address`, `status`, `audit_status`) VALUES
  (1, '星辰羽毛球馆', '天河区体育西路 88 号', 1, 1),
  (2, '云谷共享会议中心', '番禺区万博商务区 12 座', 1, 1),
  (3, '南亭自习空间', '海珠区新港中路 5 号', 1, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `status` = VALUES(`status`),
  `audit_status` = VALUES(`audit_status`);

INSERT INTO `court` (`id`, `venue_id`, `name`, `type`, `price`, `open_time`, `close_time`, `slot_minutes`, `status`, `audit_status`) VALUES
  (101, 1, '1 号场', '羽毛球', 8000,  '09:00:00', '22:00:00', 60, 1, 1),
  (102, 1, '2 号场', '羽毛球', 8000,  '09:00:00', '22:00:00', 60, 1, 1),
  (103, 1, 'VIP 场', '羽毛球', 15000, '09:00:00', '22:00:00', 60, 1, 1),
  (201, 2, '小会议室 A', '会议室', 12000, '09:00:00', '18:00:00', 60, 1, 1),
  (202, 2, '大会议室 B', '会议室', 20000, '09:00:00', '18:00:00', 60, 1, 1),
  (301, 3, '自习室 · 静音区', '自习室', 1500, '08:00:00', '23:00:00', 60, 1, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `status` = VALUES(`status`),
  `audit_status` = VALUES(`audit_status`);
