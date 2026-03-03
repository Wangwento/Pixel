-- Pixel AI头像生成平台 数据库初始化脚本
-- 创建时间: 2026-02-28
-- 更新时间: 2026-03-01 (P04用户系统)

CREATE DATABASE IF NOT EXISTS pixel DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE pixel;

-- ==================== 用户系统 ====================

-- 用户表 (新积分体系: 1张图=100积分)
-- 额度消耗顺序: 1.免费额度(VIP月度/新人礼包) -> 2.购买额度包 -> 3.积分兑换
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码(加密)',
    `nickname` VARCHAR(50) COMMENT '昵称',
    `avatar` VARCHAR(255) COMMENT '头像URL',
    `email` VARCHAR(100) COMMENT '邮箱',
    `phone` VARCHAR(20) COMMENT '手机号',
    `phone_verified` TINYINT NOT NULL DEFAULT 0 COMMENT '手机认证: 0-未认证, 1-已认证',
    `real_name` VARCHAR(50) COMMENT '真实姓名',
    `id_card` VARCHAR(50) COMMENT '身份证号(加密存储)',
    `real_name_verified` TINYINT NOT NULL DEFAULT 0 COMMENT '实名认证: 0-未认证, 1-审核中, 2-已认证, 3-认证失败',
    `points` INT NOT NULL DEFAULT 0 COMMENT '积分余额',
    `total_points` INT NOT NULL DEFAULT 0 COMMENT '累计获得积分',
    `free_quota` INT NOT NULL DEFAULT 0 COMMENT '免费额度余额(VIP赠送/新人礼包等)',
    `free_quota_total` INT NOT NULL DEFAULT 0 COMMENT '免费额度累计获得',
    `daily_limit` INT NOT NULL DEFAULT 10 COMMENT '每日生成上限(防刷)',
    `daily_used` INT NOT NULL DEFAULT 0 COMMENT '今日已生成数量',
    `daily_limit_date` DATE COMMENT '每日限制刷新日期',
    `monthly_quota` INT NOT NULL DEFAULT 0 COMMENT 'VIP月度免费额度(月卡100/年卡120)',
    `monthly_quota_used` INT NOT NULL DEFAULT 0 COMMENT '本月已用VIP额度',
    `monthly_quota_date` DATE COMMENT '月度额度刷新日期',
    `user_type` TINYINT NOT NULL DEFAULT 0 COMMENT '用户类型: 0-普通, 1-VIP',
    `vip_level` TINYINT NOT NULL DEFAULT 0 COMMENT 'VIP等级: 0-无, 1-月卡, 2-年卡',
    `vip_expire_time` DATETIME COMMENT 'VIP过期时间',
    `level` INT NOT NULL DEFAULT 1 COMMENT '用户等级(成长值)',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 额度包表(购买的额度，带有效期)
-- 注意: 这是用户花钱购买的额度，区别于免费额度(free_quota)
CREATE TABLE IF NOT EXISTS `quota_package` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `quota_total` INT NOT NULL COMMENT '总额度',
    `quota_used` INT NOT NULL DEFAULT 0 COMMENT '已用额度',
    `quota_remaining` INT NOT NULL COMMENT '剩余额度',
    `source` TINYINT NOT NULL COMMENT '来源: 1-购买, 2-积分兑换, 3-活动赠送',
    `source_desc` VARCHAR(100) COMMENT '来源描述',
    `price` DECIMAL(10,2) DEFAULT 0 COMMENT '购买价格(0表示非购买)',
    `expire_time` DATETIME NOT NULL COMMENT '过期时间',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-已失效, 1-有效, 2-已用完',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_expire_time` (`expire_time`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='额度包表(购买)';

-- 积分变动记录表 (新积分体系)
CREATE TABLE IF NOT EXISTS `points_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `points` INT NOT NULL COMMENT '积分变动(正为增加,负为消耗)',
    `balance` INT NOT NULL COMMENT '变动后余额',
    `type` TINYINT NOT NULL COMMENT '类型: 1-签到, 2-任务奖励, 3-兑换额度, 4-充值, 5-退款, 6-系统调整, 7-观看广告, 8-邀请奖励, 9-分享奖励, 10-完善资料, 11-新人礼包',
    `source` VARCHAR(50) COMMENT '来源标识',
    `description` VARCHAR(200) COMMENT '描述',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_type` (`type`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分变动记录表';

-- 额度变动记录表
CREATE TABLE IF NOT EXISTS `quota_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `quota` INT NOT NULL COMMENT '额度变动(正为增加,负为消耗)',
    `balance` INT NOT NULL COMMENT '变动后余额',
    `type` TINYINT NOT NULL COMMENT '类型: 1-生成消耗, 2-积分兑换, 3-VIP赠送, 4-购买, 5-系统调整, 6-每日重置',
    `source` VARCHAR(50) COMMENT '来源标识(如generation_id)',
    `description` VARCHAR(200) COMMENT '描述',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_type` (`type`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='额度变动记录表';

-- ==================== 标签推荐系统 ====================

-- 标签定义表
CREATE TABLE IF NOT EXISTS `tag` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '标签ID',
    `name` VARCHAR(50) NOT NULL COMMENT '标签名称',
    `name_en` VARCHAR(50) NOT NULL COMMENT '英文标识',
    `category` VARCHAR(30) NOT NULL COMMENT '分类: style/color/theme/mood',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父标签ID',
    `icon` VARCHAR(100) COMMENT '图标',
    `color` VARCHAR(20) COMMENT '颜色',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_name_en` (`name_en`),
    KEY `idx_category` (`category`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签定义表';

-- 用户标签关联表(带权重)
CREATE TABLE IF NOT EXISTS `user_tag` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `tag_id` BIGINT NOT NULL COMMENT '标签ID',
    `weight` DECIMAL(5,2) NOT NULL DEFAULT 1.00 COMMENT '权重(0.00-100.00)',
    `source` TINYINT NOT NULL DEFAULT 1 COMMENT '来源: 1-用户选择, 2-行为分析, 3-系统推断',
    `use_count` INT NOT NULL DEFAULT 0 COMMENT '使用次数(行为标签)',
    `last_use_time` DATETIME COMMENT '最后使用时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_user_tag` (`user_id`, `tag_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_tag_id` (`tag_id`),
    KEY `idx_weight` (`weight`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户标签关联表';

-- 用户行为日志表(用于分析和推荐)
CREATE TABLE IF NOT EXISTS `user_behavior` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `behavior_type` VARCHAR(30) NOT NULL COMMENT '行为类型: generate/download/like/share/view',
    `target_type` VARCHAR(30) NOT NULL COMMENT '目标类型: image/style/template',
    `target_id` VARCHAR(64) NOT NULL COMMENT '目标ID',
    `extra_data` JSON COMMENT '扩展数据(如prompt, style等)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_behavior_type` (`behavior_type`),
    KEY `idx_target` (`target_type`, `target_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为日志表';

-- ==================== 生成与订单 ====================

-- 生成记录表
CREATE TABLE IF NOT EXISTS `generation_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `prompt` TEXT NOT NULL COMMENT '提示词',
    `negative_prompt` TEXT COMMENT '负面提示词',
    `style` VARCHAR(50) COMMENT '风格模板',
    `source_image_url` VARCHAR(500) COMMENT '原图URL(图生图)',
    `result_image_url` VARCHAR(500) COMMENT '结果图URL',
    `vendor` VARCHAR(50) NOT NULL COMMENT 'AI厂商: openai/gemini/hunyuan',
    `model` VARCHAR(50) NOT NULL COMMENT '模型名称',
    `cost` DECIMAL(10,4) DEFAULT 0 COMMENT 'API调用成本',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-生成中, 1-成功, 2-失败',
    `error_message` VARCHAR(500) COMMENT '错误信息',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_vendor` (`vendor`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生成记录表';

-- 订单表
CREATE TABLE IF NOT EXISTS `order` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID',
    `order_no` VARCHAR(64) NOT NULL COMMENT '订单号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `product_type` TINYINT NOT NULL COMMENT '产品类型: 1-体验包, 2-月卡, 3-年卡',
    `product_name` VARCHAR(100) NOT NULL COMMENT '产品名称',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '订单金额',
    `pay_amount` DECIMAL(10,2) COMMENT '实付金额',
    `pay_type` TINYINT COMMENT '支付方式: 1-微信, 2-支付宝',
    `pay_time` DATETIME COMMENT '支付时间',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-待支付, 1-已支付, 2-已取消, 3-已退款',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 风格模板表
CREATE TABLE IF NOT EXISTS `style_template` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '模板ID',
    `name` VARCHAR(50) NOT NULL COMMENT '模板名称',
    `name_en` VARCHAR(50) NOT NULL COMMENT '英文名称',
    `description` VARCHAR(200) COMMENT '描述',
    `prompt_template` TEXT NOT NULL COMMENT '提示词模板',
    `cover_image` VARCHAR(255) COMMENT '封面图',
    `category` VARCHAR(50) COMMENT '分类',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_category` (`category`),
    KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风格模板表';

-- 插入默认风格模板
INSERT INTO `style_template` (`name`, `name_en`, `description`, `prompt_template`, `category`, `sort_order`) VALUES
('赛博朋克', 'cyberpunk', '霓虹灯光、科技感十足', 'cyberpunk style portrait, neon lights, futuristic, high tech, {prompt}', '科幻', 1),
('国潮风', 'guochao', '中国传统元素与现代潮流结合', 'Chinese traditional style portrait, guochao, modern fusion, {prompt}', '传统', 2),
('日系动漫', 'anime', '日本动漫风格头像', 'anime style portrait, Japanese animation, detailed, {prompt}', '动漫', 3),
('油画风格', 'oil-painting', '经典油画艺术风格', 'oil painting style portrait, classical art, masterpiece, {prompt}', '艺术', 4),
('极简头像', 'minimalist', '简洁现代的极简风格', 'minimalist portrait, clean lines, simple colors, modern, {prompt}', '简约', 5);

-- 插入默认标签
INSERT INTO `tag` (`name`, `name_en`, `category`, `sort_order`) VALUES
-- 风格标签
('赛博朋克', 'cyberpunk', 'style', 1),
('动漫', 'anime', 'style', 2),
('写实', 'realistic', 'style', 3),
('油画', 'oil-painting', 'style', 4),
('水彩', 'watercolor', 'style', 5),
('素描', 'sketch', 'style', 6),
('国潮', 'guochao', 'style', 7),
('3D渲染', '3d-render', 'style', 8),
('像素风', 'pixel-art', 'style', 9),
('极简', 'minimalist', 'style', 10),
-- 主题标签
('人物', 'portrait', 'theme', 11),
('风景', 'landscape', 'theme', 12),
('动物', 'animal', 'theme', 13),
('科幻', 'sci-fi', 'theme', 14),
('奇幻', 'fantasy', 'theme', 15),
('可爱', 'cute', 'theme', 16),
('酷炫', 'cool', 'theme', 17),
('复古', 'vintage', 'theme', 18),
-- 色调标签
('暖色调', 'warm-tone', 'color', 19),
('冷色调', 'cool-tone', 'color', 20),
('黑白', 'monochrome', 'color', 21),
('霓虹', 'neon', 'color', 22),
('柔和', 'pastel', 'color', 23),
-- 情绪标签
('活力', 'energetic', 'mood', 24),
('神秘', 'mysterious', 'mood', 25),
('优雅', 'elegant', 'mood', 26),
('可爱', 'adorable', 'mood', 27),
('酷感', 'edgy', 'mood', 28);

-- VIP等级配置表
CREATE TABLE IF NOT EXISTS `vip_config` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `level` TINYINT NOT NULL COMMENT 'VIP等级: 0-普通, 1-月卡, 2-年卡, 3-永久',
    `name` VARCHAR(30) NOT NULL COMMENT '等级名称',
    `daily_quota` INT NOT NULL COMMENT '每日免费额度',
    `points_multiplier` DECIMAL(3,1) NOT NULL DEFAULT 1.0 COMMENT '积分倍率',
    `price` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '价格(0表示无法购买)',
    `duration_days` INT NOT NULL DEFAULT 0 COMMENT '有效期(天,0表示永久)',
    `features` JSON COMMENT '特权列表',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='VIP等级配置表';

-- 插入VIP等级配置 (新积分体系: 1张图=100积分=0.2元成本)
-- 月卡: 25元/月, 每月100张额度(价值20元), 毛利5元
-- 年卡: 248元/年, 每月120张额度, 购买送500积分
INSERT INTO `vip_config` (`level`, `name`, `daily_quota`, `points_multiplier`, `price`, `duration_days`, `features`) VALUES
(0, '普通用户', 10, 1.0, 0, 0, '["每日10张生成上限", "基础风格模板", "1080p分辨率", "带水印"]'),
(1, '月卡会员', 100, 2.0, 25.00, 30, '["每月100张专属额度", "全部风格模板", "4K高清无水印", "极速生成队列", "签到积分翻倍"]'),
(2, '年卡会员', 120, 2.0, 248.00, 365, '["每月120张专属额度", "购买送500积分", "全部风格模板", "4K高清无水印", "极速生成队列", "签到积分翻倍", "专属客服"]');

-- 邀请记录表
CREATE TABLE IF NOT EXISTS `invite_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `inviter_id` BIGINT NOT NULL COMMENT '邀请人ID',
    `invitee_id` BIGINT NOT NULL COMMENT '被邀请人ID',
    `invite_code` VARCHAR(20) NOT NULL COMMENT '邀请码',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-已注册, 1-已完成首图(双方领奖)',
    `inviter_reward` INT NOT NULL DEFAULT 0 COMMENT '邀请人已领取奖励积分',
    `invitee_reward` INT NOT NULL DEFAULT 0 COMMENT '被邀请人已领取奖励积分',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `completed_at` DATETIME COMMENT '完成时间(首图生成)',
    UNIQUE KEY `uk_invitee` (`invitee_id`),
    KEY `idx_inviter` (`inviter_id`),
    KEY `idx_invite_code` (`invite_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请记录表';

-- 广告观看记录表
CREATE TABLE IF NOT EXISTS `advert_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `ad_type` VARCHAR(30) NOT NULL COMMENT '广告类型: video/banner/interstitial',
    `ad_id` VARCHAR(100) COMMENT '广告ID(来自广告平台)',
    `points_earned` INT NOT NULL COMMENT '获得积分',
    `duration` INT DEFAULT 0 COMMENT '观看时长(秒)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='广告观看记录表';

-- 积分商城商品表
CREATE TABLE IF NOT EXISTS `points_product` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `name` VARCHAR(50) NOT NULL COMMENT '商品名称',
    `description` VARCHAR(200) COMMENT '描述',
    `product_type` TINYINT NOT NULL COMMENT '类型: 1-生成额度, 2-高清下载, 3-去水印, 4-专属模型',
    `points_cost` INT NOT NULL COMMENT '所需积分',
    `value` INT NOT NULL COMMENT '商品数值(如额度数量)',
    `icon` VARCHAR(100) COMMENT '图标',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-下架, 1-上架',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分商城商品表';

-- 插入积分商城默认商品
INSERT INTO `points_product` (`name`, `description`, `product_type`, `points_cost`, `value`, `sort_order`) VALUES
('1次生成额度', '可生成1张AI头像', 1, 100, 1, 1),
('5次生成额度', '可生成5张AI头像，节省50积分', 1, 450, 5, 2),
('10次生成额度', '可生成10张AI头像，节省150积分', 1, 850, 10, 3),
('单张高清下载', '将1张图片升级为4K高清', 2, 50, 1, 4),
('单张去水印', '去除1张图片的水印', 3, 30, 1, 5);