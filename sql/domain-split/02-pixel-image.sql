CREATE DATABASE IF NOT EXISTS `pixel_image`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `pixel_image`;

CREATE TABLE IF NOT EXISTS `generation_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `prompt` TEXT NOT NULL COMMENT '提示词',
    `negative_prompt` TEXT COMMENT '负面提示词',
    `style` VARCHAR(50) COMMENT '风格模板',
    `source_image_url` VARCHAR(500) COMMENT '原图URL(图生图)',
    `result_image_url` VARCHAR(500) COMMENT '结果图URL',
    `vendor` VARCHAR(50) COMMENT 'AI厂商',
    `model` VARCHAR(50) COMMENT '模型名称',
    `cost` DECIMAL(10,4) DEFAULT 0 COMMENT 'API调用成本',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-生成中, 1-成功, 2-失败',
    `error_message` VARCHAR(500) COMMENT '错误信息',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_created_at` (`created_at`),
    KEY `idx_vendor` (`vendor`),
    KEY `idx_user_status_created_id` (`user_id`, `status`, `created_at`, `id`)
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
    UNIQUE KEY `uk_name_en` (`name_en`),
    KEY `idx_category` (`category`),
    KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风格模板表';


CREATE TABLE IF NOT EXISTS `local_message` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `message_id` VARCHAR(64) UNIQUE NOT NULL COMMENT '消息ID（唯一）',
    `topic` VARCHAR(64) NOT NULL COMMENT 'Topic',
    `tag` VARCHAR(64) COMMENT 'Tag',
    `content` TEXT NOT NULL COMMENT '消息内容（JSON）',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待发送，1-已发送，2-发送失败',
    `retry_count` INT DEFAULT 0 COMMENT '重试次数',
    `max_retry` INT DEFAULT 3 COMMENT '最大重试次数',
    `next_retry_time` DATETIME COMMENT '下次重试时间',
    `error_message` VARCHAR(500) COMMENT '错误信息',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY `idx_status_retry` (`status`, `next_retry_time`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表';

CREATE TABLE IF NOT EXISTS `undo_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `branch_id` BIGINT NOT NULL,
    `xid` VARCHAR(100) NOT NULL,
    `context` VARCHAR(128) NOT NULL,
    `rollback_info` LONGBLOB NOT NULL,
    `log_status` INT NOT NULL,
    `log_created` DATETIME NOT NULL,
    `log_modified` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Seata AT模式undo日志表';
