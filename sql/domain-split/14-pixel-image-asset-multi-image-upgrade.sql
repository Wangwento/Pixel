USE `pixel_image`;

SET @image_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'image_asset'
      AND COLUMN_NAME = 'image_index'
);

SET @ddl = IF(
    @image_index_exists = 0,
    'ALTER TABLE `image_asset` ADD COLUMN `image_index` INT NOT NULL DEFAULT 0 COMMENT ''生成结果序号，从0开始'' AFTER `generation_record_id`',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_unique_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'image_asset'
      AND INDEX_NAME = 'uk_generation_record_id'
);

SET @ddl = IF(
    @old_unique_exists > 0,
    'ALTER TABLE `image_asset` DROP INDEX `uk_generation_record_id`',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @new_unique_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'image_asset'
      AND INDEX_NAME = 'uk_generation_record_image_index'
);

SET @ddl = IF(
    @new_unique_exists = 0,
    'ALTER TABLE `image_asset` ADD UNIQUE INDEX `uk_generation_record_image_index` (`generation_record_id`, `image_index`)',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
