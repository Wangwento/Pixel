USE `pixel_image`;

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

INSERT INTO `image_asset`
(`user_id`, `generation_record_id`, `image_index`, `folder_id`, `title`, `image_url`, `prompt`, `style`, `source_type`, `created_at`, `updated_at`)
SELECT
    gr.`user_id`,
    gr.`id`,
    0,
    0,
    CASE
        WHEN gr.`prompt` IS NULL OR TRIM(gr.`prompt`) = '' THEN CONCAT('作品-', DATE_FORMAT(gr.`created_at`, '%m%d%H%i'))
        WHEN CHAR_LENGTH(TRIM(gr.`prompt`)) > 30 THEN CONCAT(SUBSTRING(TRIM(gr.`prompt`), 1, 30), '...')
        ELSE TRIM(gr.`prompt`)
    END,
    gr.`result_image_url`,
    gr.`prompt`,
    gr.`style`,
    'GENERATED',
    gr.`created_at`,
    NOW()
FROM `generation_record` gr
WHERE gr.`status` = 1
  AND gr.`result_image_url` IS NOT NULL
  AND gr.`result_image_url` <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM `image_asset` ia
      WHERE ia.`generation_record_id` = gr.`id`
  );
