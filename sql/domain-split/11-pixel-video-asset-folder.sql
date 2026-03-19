-- 视频资产添加文件夹字段
ALTER TABLE video_asset ADD COLUMN folder_id BIGINT DEFAULT 0 COMMENT '文件夹ID，0表示根目录';
ALTER TABLE video_asset ADD INDEX idx_user_folder (user_id, folder_id);
