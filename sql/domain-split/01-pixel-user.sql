CREATE DATABASE IF NOT EXISTS `pixel_user`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `pixel_user`;

CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码(加密)',
    `nickname` VARCHAR(50) COMMENT '昵称',
    `avatar` VARCHAR(255) COMMENT '头像URL',
    `email` VARCHAR(100) COMMENT '邮箱',
    `email_verified` TINYINT NOT NULL DEFAULT 0 COMMENT '邮箱认证: 0-未认证, 1-已认证',
    `phone` VARCHAR(20) COMMENT '手机号',
    `phone_verified` TINYINT NOT NULL DEFAULT 0 COMMENT '手机认证: 0-未认证, 1-已认证',
    `real_name` VARCHAR(50) COMMENT '真实姓名',
    `id_card` VARCHAR(50) COMMENT '身份证号(加密存储)',
    `real_name_verified` TINYINT NOT NULL DEFAULT 0 COMMENT '实名认证: 0-未认证, 1-审核中, 2-已认证, 3-认证失败',
    `points` INT NOT NULL DEFAULT 0 COMMENT '积分余额',
    `total_points` INT NOT NULL DEFAULT 0 COMMENT '累计获得积分',
    `free_quota` INT NOT NULL DEFAULT 0 COMMENT '免费额度余额',
    `free_quota_total` INT NOT NULL DEFAULT 0 COMMENT '免费额度累计获得',
    `daily_limit` INT NOT NULL DEFAULT 10 COMMENT '每日生成上限',
    `daily_used` INT NOT NULL DEFAULT 0 COMMENT '今日已生成数量',
    `daily_limit_date` DATE COMMENT '每日限制刷新日期',
    `monthly_quota` INT NOT NULL DEFAULT 0 COMMENT 'VIP月度免费额度',
    `monthly_quota_used` INT NOT NULL DEFAULT 0 COMMENT '本月已用VIP额度',
    `monthly_quota_date` DATE COMMENT '月度额度刷新日期',
    `user_type` TINYINT NOT NULL DEFAULT 0 COMMENT '用户类型: 0-普通, 1-VIP',
    `vip_level` TINYINT NOT NULL DEFAULT 0 COMMENT 'VIP等级',
    `vip_expire_time` DATETIME COMMENT 'VIP过期时间',
    `level` INT NOT NULL DEFAULT 1 COMMENT '用户等级',
    `exp` INT NOT NULL DEFAULT 0 COMMENT '当前经验值',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-正常',
    `last_sign_date` DATE COMMENT '上次签到日期',
    `continuous_sign_days` INT NOT NULL DEFAULT 0 COMMENT '连续签到天数',
    `invite_code` VARCHAR(20) COMMENT '我的邀请码',
    `invited_by` BIGINT COMMENT '邀请人ID',
    `profile_completed` TINYINT NOT NULL DEFAULT 0 COMMENT '资料是否完善: 0-否, 1-是',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_invite_code` (`invite_code`),
    KEY `idx_phone` (`phone`),
    KEY `idx_email` (`email`),
    KEY `idx_user_type` (`user_type`),
    KEY `idx_vip_level` (`vip_level`),
    KEY `idx_invited_by` (`invited_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户主档表';

CREATE TABLE IF NOT EXISTS `quota_package` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `quota_total` INT NOT NULL COMMENT '总额度',
    `quota_used` INT NOT NULL DEFAULT 0 COMMENT '已用额度',
    `quota_remaining` INT NOT NULL COMMENT '剩余额度',
    `source` TINYINT NOT NULL COMMENT '来源: 1-购买, 2-积分兑换, 3-活动赠送',
    `source_desc` VARCHAR(100) COMMENT '来源描述',
    `price` DECIMAL(10,2) DEFAULT 0 COMMENT '购买价格',
    `expire_time` DATETIME NOT NULL COMMENT '过期时间',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-已失效, 1-有效, 2-已用完',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_expire_time` (`expire_time`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='额度包表';

CREATE TABLE IF NOT EXISTS `points_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `points` INT NOT NULL COMMENT '积分变动',
    `balance` INT NOT NULL COMMENT '变动后余额',
    `type` TINYINT NOT NULL COMMENT '类型: 1-签到, 2-任务奖励, 3-兑换额度, 4-充值, 5-退款, 6-系统调整, 7-观看广告, 8-邀请奖励, 9-分享奖励, 10-完善资料, 11-新人礼包, 12-成长活动',
    `source` VARCHAR(50) COMMENT '来源标识',
    `description` VARCHAR(200) COMMENT '描述',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_type` (`type`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分变动记录表';

CREATE TABLE IF NOT EXISTS `quota_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `quota` INT NOT NULL COMMENT '额度变动',
    `balance` INT NOT NULL COMMENT '变动后余额',
    `type` TINYINT NOT NULL COMMENT '类型: 1-生成消耗, 2-积分兑换, 3-VIP赠送, 4-购买, 5-系统调整, 6-每日重置, 7-成长活动',
    `source` VARCHAR(50) COMMENT '来源标识',
    `description` VARCHAR(200) COMMENT '描述',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_type` (`type`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='额度变动记录表';

CREATE TABLE IF NOT EXISTS `tag` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '标签ID',
    `name` VARCHAR(50) NOT NULL COMMENT '标签名称',
    `name_en` VARCHAR(50) NOT NULL COMMENT '英文标识',
    `category` VARCHAR(30) NOT NULL COMMENT '分类',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父标签ID',
    `icon` VARCHAR(100) COMMENT '图标',
    `color` VARCHAR(20) COMMENT '颜色',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_name_en` (`name_en`),
    KEY `idx_category` (`category`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签定义表';

CREATE TABLE IF NOT EXISTS `user_tag` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `tag_id` BIGINT NOT NULL COMMENT '标签ID',
    `weight` DECIMAL(5,2) NOT NULL DEFAULT 1.00 COMMENT '权重',
    `source` VARCHAR(30) COMMENT '来源',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_user_tag` (`user_id`, `tag_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户标签关联表';

CREATE TABLE IF NOT EXISTS `user_behavior` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `behavior_type` VARCHAR(30) NOT NULL COMMENT '行为类型',
    `target_type` VARCHAR(30) NOT NULL COMMENT '目标类型',
    `target_id` VARCHAR(64) NOT NULL COMMENT '目标ID',
    `extra_data` JSON COMMENT '扩展数据',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_behavior_type` (`behavior_type`),
    KEY `idx_target` (`target_type`, `target_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为日志表';

CREATE TABLE IF NOT EXISTS `growth_activity` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `activity_code` VARCHAR(32) NOT NULL COMMENT '活动编码',
    `activity_name` VARCHAR(100) NOT NULL COMMENT '活动名称',
    `trigger_type` VARCHAR(30) NOT NULL COMMENT '触发类型: register/profile_complete/email_bind/phone_bind/real_name_verify/first_generate/invite_success/manual',
    `description` VARCHAR(255) COMMENT '活动描述',
    `start_time` DATETIME COMMENT '开始时间，空表示立即生效',
    `end_time` DATETIME COMMENT '结束时间，空表示长期有效',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-停用, 1-启用',
    `once_per_user` TINYINT NOT NULL DEFAULT 1 COMMENT '是否每用户仅一次: 0-否, 1-是',
    `auto_grant` TINYINT NOT NULL DEFAULT 1 COMMENT '是否自动发放: 0-否, 1-是',
    `priority` INT NOT NULL DEFAULT 0 COMMENT '优先级',
    `ext_config` JSON COMMENT '扩展配置',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_activity_code` (`activity_code`),
    KEY `idx_status_trigger` (`status`, `trigger_type`),
    KEY `idx_time_range` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户增长活动表';

CREATE TABLE IF NOT EXISTS `growth_activity_reward` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `activity_id` BIGINT NOT NULL COMMENT '活动ID',
    `reward_type` VARCHAR(30) NOT NULL COMMENT '奖励类型: points/quota/vip_days',
    `reward_value` DECIMAL(12,2) NOT NULL COMMENT '奖励值',
    `reward_unit` VARCHAR(20) NOT NULL COMMENT '奖励单位: points/quota/days',
    `expire_days` INT NOT NULL DEFAULT 0 COMMENT '奖励有效期天数，0表示长期有效',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-停用, 1-启用',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `extra_config` JSON COMMENT '扩展配置',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_activity_reward` (`activity_id`, `reward_type`, `sort_order`),
    KEY `idx_activity_status` (`activity_id`, `status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户增长活动奖励表';

CREATE TABLE IF NOT EXISTS `user_growth_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `activity_id` BIGINT NOT NULL COMMENT '活动ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `biz_key` VARCHAR(100) NOT NULL COMMENT '业务幂等键，如register/profile_complete',
    `trigger_type` VARCHAR(30) NOT NULL COMMENT '触发类型',
    `trigger_source` VARCHAR(50) COMMENT '触发来源，如auth.register',
    `hit_status` TINYINT NOT NULL DEFAULT 1 COMMENT '命中状态: 1-命中, 2-未命中, 3-失效',
    `reward_status` TINYINT NOT NULL DEFAULT 0 COMMENT '奖励状态: 0-待发放, 1-已发放, 2-发放失败, 3-已回滚',
    `reward_snapshot` JSON COMMENT '奖励快照',
    `triggered_at` DATETIME COMMENT '触发时间',
    `granted_at` DATETIME COMMENT '发放时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_activity_user_biz` (`activity_id`, `user_id`, `biz_key`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_trigger_type` (`trigger_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户增长活动记录表';

INSERT INTO `growth_activity`
(`activity_code`, `activity_name`, `trigger_type`, `description`, `status`, `once_per_user`, `auto_grant`, `priority`)
VALUES
('register-gift', '注册新人礼包', 'register', '用户注册成功后通知中心手动领取新人礼包', 1, 1, 0, 10)
ON DUPLICATE KEY UPDATE
    `activity_name` = VALUES(`activity_name`),
    `description` = VALUES(`description`),
    `status` = VALUES(`status`),
    `once_per_user` = VALUES(`once_per_user`),
    `auto_grant` = VALUES(`auto_grant`),
    `priority` = VALUES(`priority`);

INSERT INTO `growth_activity_reward`
(`activity_id`, `reward_type`, `reward_value`, `reward_unit`, `expire_days`, `status`, `sort_order`)
SELECT `id`, 'points', 20, 'points', 0, 1, 10
FROM `growth_activity`
WHERE `activity_code` = 'register-gift'
ON DUPLICATE KEY UPDATE
    `reward_value` = VALUES(`reward_value`),
    `reward_unit` = VALUES(`reward_unit`),
    `expire_days` = VALUES(`expire_days`),
    `status` = VALUES(`status`);

INSERT INTO `growth_activity_reward`
(`activity_id`, `reward_type`, `reward_value`, `reward_unit`, `expire_days`, `status`, `sort_order`)
SELECT `id`, 'quota', 3, 'quota', 0, 1, 20
FROM `growth_activity`
WHERE `activity_code` = 'register-gift'
ON DUPLICATE KEY UPDATE
    `reward_value` = VALUES(`reward_value`),
    `reward_unit` = VALUES(`reward_unit`),
    `expire_days` = VALUES(`expire_days`),
    `status` = VALUES(`status`);

INSERT INTO `growth_activity`
(`activity_code`, `activity_name`, `trigger_type`, `description`, `status`, `once_per_user`, `auto_grant`, `priority`)
VALUES
('profile-complete-gift', '完善资料奖励', 'profile_complete', '完善昵称和头像后可在通知中心手动领取奖励', 1, 1, 0, 20)
ON DUPLICATE KEY UPDATE
    `trigger_type` = VALUES(`trigger_type`),
    `activity_name` = VALUES(`activity_name`),
    `description` = VALUES(`description`),
    `status` = VALUES(`status`),
    `once_per_user` = VALUES(`once_per_user`),
    `auto_grant` = VALUES(`auto_grant`),
    `priority` = VALUES(`priority`);

INSERT INTO `growth_activity_reward`
(`activity_id`, `reward_type`, `reward_value`, `reward_unit`, `expire_days`, `status`, `sort_order`)
SELECT `id`, 'points', 50, 'points', 0, 1, 10
FROM `growth_activity`
WHERE `activity_code` = 'profile-complete-gift'
ON DUPLICATE KEY UPDATE
    `reward_value` = VALUES(`reward_value`),
    `reward_unit` = VALUES(`reward_unit`),
    `expire_days` = VALUES(`expire_days`),
    `status` = VALUES(`status`);

INSERT INTO `growth_activity`
(`activity_code`, `activity_name`, `trigger_type`, `description`, `status`, `once_per_user`, `auto_grant`, `priority`)
VALUES
('bind-email-gift', '绑定邮箱奖励', 'email_bind', '绑定邮箱后可在通知中心手动领取奖励', 1, 1, 0, 30)
ON DUPLICATE KEY UPDATE
    `trigger_type` = VALUES(`trigger_type`),
    `activity_name` = VALUES(`activity_name`),
    `description` = VALUES(`description`),
    `status` = VALUES(`status`),
    `once_per_user` = VALUES(`once_per_user`),
    `auto_grant` = VALUES(`auto_grant`),
    `priority` = VALUES(`priority`);

INSERT INTO `growth_activity_reward`
(`activity_id`, `reward_type`, `reward_value`, `reward_unit`, `expire_days`, `status`, `sort_order`)
SELECT `id`, 'points', 20, 'points', 0, 1, 10
FROM `growth_activity`
WHERE `activity_code` = 'bind-email-gift'
ON DUPLICATE KEY UPDATE
    `reward_value` = VALUES(`reward_value`),
    `reward_unit` = VALUES(`reward_unit`),
    `expire_days` = VALUES(`expire_days`),
    `status` = VALUES(`status`);

INSERT INTO `growth_activity`
(`activity_code`, `activity_name`, `trigger_type`, `description`, `status`, `once_per_user`, `auto_grant`, `priority`)
VALUES
('bind-phone-gift', '绑定手机号奖励', 'phone_bind', '绑定手机号后可在通知中心手动领取奖励', 1, 1, 0, 40)
ON DUPLICATE KEY UPDATE
    `trigger_type` = VALUES(`trigger_type`),
    `activity_name` = VALUES(`activity_name`),
    `description` = VALUES(`description`),
    `status` = VALUES(`status`),
    `once_per_user` = VALUES(`once_per_user`),
    `auto_grant` = VALUES(`auto_grant`),
    `priority` = VALUES(`priority`);

INSERT INTO `growth_activity_reward`
(`activity_id`, `reward_type`, `reward_value`, `reward_unit`, `expire_days`, `status`, `sort_order`)
SELECT `id`, 'quota', 3, 'quota', 0, 1, 10
FROM `growth_activity`
WHERE `activity_code` = 'bind-phone-gift'
ON DUPLICATE KEY UPDATE
    `reward_value` = VALUES(`reward_value`),
    `reward_unit` = VALUES(`reward_unit`),
    `expire_days` = VALUES(`expire_days`),
    `status` = VALUES(`status`);

INSERT INTO `growth_activity`
(`activity_code`, `activity_name`, `trigger_type`, `description`, `status`, `once_per_user`, `auto_grant`, `priority`)
VALUES
('real-name-verify-gift', '实名认证奖励', 'real_name_verify', '完成实名认证后可在通知中心手动领取奖励', 1, 1, 0, 50)
ON DUPLICATE KEY UPDATE
    `trigger_type` = VALUES(`trigger_type`),
    `activity_name` = VALUES(`activity_name`),
    `description` = VALUES(`description`),
    `status` = VALUES(`status`),
    `once_per_user` = VALUES(`once_per_user`),
    `auto_grant` = VALUES(`auto_grant`),
    `priority` = VALUES(`priority`);

INSERT INTO `growth_activity_reward`
(`activity_id`, `reward_type`, `reward_value`, `reward_unit`, `expire_days`, `status`, `sort_order`)
SELECT `id`, 'quota', 5, 'quota', 0, 1, 10
FROM `growth_activity`
WHERE `activity_code` = 'real-name-verify-gift'
ON DUPLICATE KEY UPDATE
    `reward_value` = VALUES(`reward_value`),
    `reward_unit` = VALUES(`reward_unit`),
    `expire_days` = VALUES(`expire_days`),
    `status` = VALUES(`status`);

CREATE TABLE IF NOT EXISTS `invite_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `inviter_id` BIGINT NOT NULL COMMENT '邀请人ID',
    `invitee_id` BIGINT NOT NULL COMMENT '被邀请人ID',
    `invite_code` VARCHAR(20) NOT NULL COMMENT '邀请码',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态',
    `inviter_reward` INT NOT NULL DEFAULT 0 COMMENT '邀请人奖励积分',
    `invitee_reward` INT NOT NULL DEFAULT 0 COMMENT '被邀请人奖励积分',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `completed_at` DATETIME COMMENT '完成时间',
    UNIQUE KEY `uk_invitee` (`invitee_id`),
    KEY `idx_inviter` (`inviter_id`),
    KEY `idx_invite_code` (`invite_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请记录表';

CREATE TABLE IF NOT EXISTS `advert_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `ad_type` VARCHAR(30) NOT NULL COMMENT '广告类型',
    `ad_id` VARCHAR(100) COMMENT '广告ID',
    `points_earned` INT NOT NULL COMMENT '获得积分',
    `duration` INT DEFAULT 0 COMMENT '观看时长',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='广告观看记录表';
