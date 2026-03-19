USE `pixel_image`;

SET @video_asset_id_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'hot_image'
      AND COLUMN_NAME = 'video_asset_id'
);

SET @ddl = IF(
    @video_asset_id_exists = 0,
    'ALTER TABLE `hot_image` ADD COLUMN `video_asset_id` BIGINT NULL COMMENT ''关联的视频资产ID(逻辑引用 pixel_vedio.video_asset.id)'' AFTER `image_asset_id`',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @media_type_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'hot_image'
      AND COLUMN_NAME = 'media_type'
);

SET @ddl = IF(
    @media_type_exists = 0,
    'ALTER TABLE `hot_image` ADD COLUMN `media_type` VARCHAR(16) NOT NULL DEFAULT ''image'' COMMENT ''媒体类型: image, video'' AFTER `image_url`',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @cover_url_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'hot_image'
      AND COLUMN_NAME = 'cover_url'
);

SET @ddl = IF(
    @cover_url_exists = 0,
    'ALTER TABLE `hot_image` ADD COLUMN `cover_url` VARCHAR(512) NULL COMMENT ''视频封面URL (仅video类型)'' AFTER `media_type`',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `hot_image`
    MODIFY COLUMN `image_asset_id` BIGINT NULL COMMENT '关联的图片资产ID',
    MODIFY COLUMN `image_url` VARCHAR(512) NOT NULL COMMENT '媒体资源URL(图片URL或视频URL)',
    MODIFY COLUMN `media_type` VARCHAR(16) NOT NULL DEFAULT 'image' COMMENT '媒体类型: image, video';

SET @idx_user_image_asset_status_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'hot_image'
      AND INDEX_NAME = 'idx_user_image_asset_status'
);

SET @ddl = IF(
    @idx_user_image_asset_status_exists = 0,
    'ALTER TABLE `hot_image` ADD INDEX `idx_user_image_asset_status` (`user_id`, `image_asset_id`, `status`)',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_user_video_asset_status_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'hot_image'
      AND INDEX_NAME = 'idx_user_video_asset_status'
);

SET @ddl = IF(
    @idx_user_video_asset_status_exists = 0,
    'ALTER TABLE `hot_image` ADD INDEX `idx_user_video_asset_status` (`user_id`, `video_asset_id`, `status`)',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
