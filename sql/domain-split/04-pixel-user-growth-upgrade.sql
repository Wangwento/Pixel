USE `pixel_user`;

SET @email_verified_column_exists = (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user'
      AND COLUMN_NAME = 'email_verified'
);
SET @email_verified_alter_sql = IF(
    @email_verified_column_exists = 0,
    'ALTER TABLE `user` ADD COLUMN `email_verified` TINYINT NOT NULL DEFAULT 0 COMMENT ''邮箱认证: 0-未认证, 1-已认证'' AFTER `email`',
    'SELECT 1'
);
PREPARE email_verified_stmt FROM @email_verified_alter_sql;
EXECUTE email_verified_stmt;
DEALLOCATE PREPARE email_verified_stmt;

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
