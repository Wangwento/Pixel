USE `pixel_user`;

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
