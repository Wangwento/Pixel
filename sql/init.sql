-- Pixel AI头像生成平台 数据库初始化脚本
-- 创建时间: 2026-02-28

CREATE DATABASE IF NOT EXISTS pixel DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE pixel;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码(加密)',
    `nickname` VARCHAR(50) COMMENT '昵称',
    `avatar` VARCHAR(255) COMMENT '头像URL',
    `email` VARCHAR(100) COMMENT '邮箱',
    `phone` VARCHAR(20) COMMENT '手机号',
    `quota` INT NOT NULL DEFAULT 3 COMMENT '剩余生成次数',
    `vip_level` TINYINT NOT NULL DEFAULT 0 COMMENT 'VIP等级: 0-普通, 1-月卡, 2-年卡',
    `vip_expire_time` DATETIME COMMENT 'VIP过期时间',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-正常',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_phone` (`phone`),
    KEY `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

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