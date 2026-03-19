-- AI模型分类和图片输入支持升级
-- 添加模型分类和是否支持图片输入字段
USE pixel_admin;

ALTER TABLE ai_model
ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'image' COMMENT '模型分类: image-绘图, video-视频, audio-音频, chat-聊天' AFTER model_type,
ADD COLUMN supports_image_input TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否支持图片输入: 0-不支持, 1-支持' AFTER category;

-- 为已有数据设置默认值（根据model_type推断）
UPDATE ai_model SET category = 'image' WHERE model_type LIKE '%image%';
UPDATE ai_model SET category = 'video' WHERE model_type LIKE '%video%';