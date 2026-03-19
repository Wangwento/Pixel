-- Pixel AI头像生成平台 数据库初始化脚本
-- 创建时间: 2026-02-28
-- 更新时间: 2026-03-01 (P04用户系统)

CREATE DATABASE IF NOT EXISTS pixel DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE pixel;

-- ==================== 用户系统 ====================

-- 用户表 (新积分体系: 1张图=100积分)
-- 额度消耗顺序: 1.免费额度(赠送/积分兑换/购买) -> 2.VIP月度额度
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
    `free_quota` INT NOT NULL DEFAULT 0 COMMENT '免费额度余额(赠送/积分兑换/购买额度卡，不含VIP月度)',
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
    `type` TINYINT NOT NULL COMMENT '类型: 1-签到, 2-任务奖励, 3-兑换额度, 4-充值, 5-退款, 6-系统调整, 7-观看广告, 8-邀请奖励, 9-分享奖励, 10-完善资料, 11-新人礼包, 12-成长活动',
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
    `type` TINYINT NOT NULL COMMENT '类型: 1-生成消耗, 2-积分兑换, 3-VIP赠送, 4-购买, 5-系统调整, 6-每日重置, 7-成长活动',
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

CREATE TABLE IF NOT EXISTS `asset_folder` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文件夹ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父文件夹ID，0表示根目录',
    `folder_name` VARCHAR(32) NOT NULL COMMENT '文件夹名称',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序值',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_user_parent_name` (`user_id`, `parent_id`, `folder_name`),
    KEY `idx_user_parent_sort` (`user_id`, `parent_id`, `sort_order`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片资产文件夹表';

CREATE TABLE IF NOT EXISTS `image_asset` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '资产ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `generation_record_id` BIGINT COMMENT '生成记录ID',
    `image_index` INT NOT NULL DEFAULT 0 COMMENT '生成结果序号，从0开始',
    `folder_id` BIGINT NOT NULL DEFAULT 0 COMMENT '文件夹ID，0表示根目录',
    `title` VARCHAR(120) NOT NULL COMMENT '图片标题',
    `image_url` VARCHAR(500) NOT NULL COMMENT '图片URL',
    `prompt` TEXT COMMENT '原始提示词',
    `style` VARCHAR(50) COMMENT '风格',
    `source_type` VARCHAR(20) NOT NULL DEFAULT 'GENERATED' COMMENT '来源类型',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_generation_record_image_index` (`generation_record_id`, `image_index`),
    KEY `idx_user_folder_created_id` (`user_id`, `folder_id`, `created_at`, `id`),
    KEY `idx_user_title` (`user_id`, `title`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片资产表';

CREATE TABLE IF NOT EXISTS `video_generation_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `task_id` VARCHAR(128) NOT NULL COMMENT '系统任务ID',
    `provider_task_id` VARCHAR(128) NOT NULL COMMENT '上游任务ID',
    `request_type` VARCHAR(20) NOT NULL DEFAULT 'TEXT2VIDEO' COMMENT '请求类型: TEXT2VIDEO/IMAGE2VIDEO',
    `prompt` TEXT NOT NULL COMMENT '提示词',
    `source_images` TEXT COMMENT '参考图列表(JSON数组)',
    `vendor` VARCHAR(50) COMMENT 'AI厂商',
    `model` VARCHAR(50) COMMENT '模型名称',
    `aspect_ratio` VARCHAR(16) COMMENT '输出比例',
    `duration` VARCHAR(8) COMMENT '视频时长(秒)',
    `cost` DECIMAL(10,4) DEFAULT 0 COMMENT '视频生成成本',
    `hd` TINYINT NOT NULL DEFAULT 0 COMMENT '是否高清',
    `notify_hook` VARCHAR(500) COMMENT '回调地址',
    `watermark` TINYINT NOT NULL DEFAULT 0 COMMENT '是否带水印',
    `private_mode` TINYINT NOT NULL DEFAULT 0 COMMENT '是否私有',
    `status` VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED' COMMENT '任务状态',
    `progress` VARCHAR(20) COMMENT '任务进度',
    `result_video_url` VARCHAR(512) COMMENT '结果视频URL',
    `cover_url` VARCHAR(512) COMMENT '视频封面URL',
    `fail_reason` VARCHAR(500) COMMENT '失败原因',
    `submit_time` DATETIME COMMENT '提交时间',
    `start_time` DATETIME COMMENT '开始时间',
    `finish_time` DATETIME COMMENT '完成时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_task_id` (`task_id`),
    UNIQUE KEY `uk_provider_task_id` (`provider_task_id`),
    KEY `idx_video_user_status_created_id` (`user_id`, `status`, `created_at`, `id`),
    KEY `idx_video_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频生成记录表';

CREATE TABLE IF NOT EXISTS `video_asset` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '视频资产ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `generation_record_id` BIGINT COMMENT '视频生成记录ID',
    `title` VARCHAR(120) NOT NULL COMMENT '视频标题',
    `video_url` VARCHAR(512) NOT NULL COMMENT '视频URL',
    `cover_url` VARCHAR(512) COMMENT '视频封面URL',
    `prompt` TEXT COMMENT '原始提示词',
    `duration` VARCHAR(8) COMMENT '视频时长(秒)',
    `source_type` VARCHAR(20) NOT NULL DEFAULT 'GENERATED' COMMENT '来源类型',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_video_generation_record_id` (`generation_record_id`),
    KEY `idx_video_asset_user_created_id` (`user_id`, `created_at`, `id`),
    KEY `idx_video_asset_user_title` (`user_id`, `title`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频资产表';

CREATE TABLE IF NOT EXISTS `hot_image` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '提交用户ID',
    `image_asset_id` BIGINT COMMENT '关联的图片资产ID',
    `video_asset_id` BIGINT COMMENT '关联的视频资产ID',
    `image_url` VARCHAR(512) NOT NULL COMMENT '媒体资源URL(图片URL或视频URL)',
    `media_type` VARCHAR(16) NOT NULL DEFAULT 'image' COMMENT '媒体类型: image, video',
    `cover_url` VARCHAR(512) COMMENT '视频封面URL (仅video类型)',
    `title` VARCHAR(100) COMMENT '标题',
    `description` VARCHAR(500) COMMENT '描述',
    `status` TINYINT DEFAULT 0 COMMENT '0=待审核, 1=已通过, 2=已拒绝, 3=已下架',
    `reject_reason` VARCHAR(200) COMMENT '拒绝原因',
    `reviewer_id` BIGINT COMMENT '审核人ID',
    `reviewed_at` DATETIME COMMENT '审核时间',
    `reward_claimed` TINYINT DEFAULT 0 COMMENT '0=未领取, 1=已领取 (仅status=1时有效)',
    `reward_points` INT DEFAULT 100 COMMENT '奖励积分数',
    `like_count` INT DEFAULT 0 COMMENT '点赞数',
    `collect_count` INT DEFAULT 0 COMMENT '收藏数',
    `comment_count` INT DEFAULT 0 COMMENT '评论数',
    `claimed_at` DATETIME COMMENT '领取时间',
    `created_at` DATETIME DEFAULT NOW() COMMENT '创建时间',
    KEY `idx_hot_user_id` (`user_id`),
    KEY `idx_hot_status` (`status`),
    KEY `idx_hot_created_at` (`created_at`),
    KEY `idx_hot_user_image_asset_status` (`user_id`, `image_asset_id`, `status`),
    KEY `idx_hot_user_video_asset_status` (`user_id`, `video_asset_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='热门内容';

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
    `negative_prompt` TEXT COMMENT '负面提示词',
    `cover_image` VARCHAR(255) COMMENT '封面图',
    `category` VARCHAR(50) COMMENT '分类',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_category` (`category`),
    KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风格模板表';

-- 插入默认风格模板
INSERT INTO `style_template` (`name`, `name_en`, `description`, `prompt_template`, `negative_prompt`, `category`, `sort_order`) VALUES
('赛博朋克', 'cyberpunk', '霓虹灯光、科技感十足', 'cyberpunk style portrait, neon lights, futuristic, high tech, {prompt}', 'low quality, blurry, lowres, deformed face, extra fingers, extra limbs, text, watermark, logo', '科幻', 1),
('国潮风', 'guochao', '中国传统元素与现代潮流结合', 'Chinese traditional style portrait, guochao, modern fusion, {prompt}', 'western clothing, low quality, blurry, deformed face, extra fingers, extra limbs, text, watermark, logo', '传统', 2),
('日系动漫', 'anime', '日本动漫风格头像', 'anime style portrait, Japanese animation, detailed, {prompt}', 'realistic photo, low quality, blurry, bad anatomy, extra fingers, extra limbs, text, watermark', '动漫', 3),
('油画风格', 'oil-painting', '经典油画艺术风格', 'oil painting style portrait, classical art, masterpiece, {prompt}', 'photo, 3d render, low quality, blurry, deformed face, extra fingers, extra limbs, text, watermark', '艺术', 4),
('极简头像', 'minimalist', '简洁现代的极简风格', 'minimalist portrait, clean lines, simple colors, modern, {prompt}', 'busy background, clutter, photorealistic texture, low quality, blurry, deformed face, text, watermark', '简约', 5);

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

-- ==================== 用户增长/通知任务系统 ====================

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
    `biz_key` VARCHAR(100) NOT NULL COMMENT '业务幂等键，如register/profile_complete/email_bind/phone_bind/real_name_verify',
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
