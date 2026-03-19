USE `pixel_image`;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'style_template'
      AND COLUMN_NAME = 'negative_prompt'
);

SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE `style_template` ADD COLUMN `negative_prompt` TEXT COMMENT ''负面提示词'' AFTER `prompt_template`',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `style_template`
SET `negative_prompt` = 'low quality, blurry, lowres, deformed face, extra fingers, extra limbs, text, watermark, logo'
WHERE `name_en` = 'cyberpunk'
  AND (`negative_prompt` IS NULL OR TRIM(`negative_prompt`) = '');

UPDATE `style_template`
SET `negative_prompt` = 'western clothing, low quality, blurry, deformed face, extra fingers, extra limbs, text, watermark, logo'
WHERE `name_en` = 'guochao'
  AND (`negative_prompt` IS NULL OR TRIM(`negative_prompt`) = '');

UPDATE `style_template`
SET `negative_prompt` = 'realistic photo, low quality, blurry, bad anatomy, extra fingers, extra limbs, text, watermark'
WHERE `name_en` = 'anime'
  AND (`negative_prompt` IS NULL OR TRIM(`negative_prompt`) = '');

UPDATE `style_template`
SET `negative_prompt` = 'photo, 3d render, low quality, blurry, deformed face, extra fingers, extra limbs, text, watermark'
WHERE `name_en` = 'oil-painting'
  AND (`negative_prompt` IS NULL OR TRIM(`negative_prompt`) = '');

UPDATE `style_template`
SET `negative_prompt` = 'busy background, clutter, photorealistic texture, low quality, blurry, deformed face, text, watermark'
WHERE `name_en` = 'minimalist'
  AND (`negative_prompt` IS NULL OR TRIM(`negative_prompt`) = '');
