CREATE DATABASE IF NOT EXISTS `pixel_vedio`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `pixel_vedio`;

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
    KEY `idx_user_status_created_id` (`user_id`, `status`, `created_at`, `id`),
    KEY `idx_created_at` (`created_at`)
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
    UNIQUE KEY `uk_generation_record_id` (`generation_record_id`),
    KEY `idx_user_created_id` (`user_id`, `created_at`, `id`),
    KEY `idx_user_title` (`user_id`, `title`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频资产表';
