CREATE DATABASE IF NOT EXISTS `pixel_audio`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `pixel_audio`;

CREATE TABLE IF NOT EXISTS audio_generation_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    task_id VARCHAR(128) NOT NULL COMMENT '平台任务ID（编码后）',
    provider_task_id VARCHAR(128) NOT NULL COMMENT '上游任务ID',
    request_type VARCHAR(64) NOT NULL COMMENT '任务类型：generate/extend/upload_extend/artist_consistency/gen_stem',
    prompt TEXT COMMENT '提示词/歌词',
    title VARCHAR(255) COMMENT '标题',
    tags VARCHAR(500) COMMENT '风格标签',
    continue_clip_id VARCHAR(128) COMMENT '续写来源clip_id',
    vendor VARCHAR(100) COMMENT '供应商编码',
    model VARCHAR(100) COMMENT '模型编码',
    cost DECIMAL(10,4) DEFAULT 0.0000 COMMENT '本次生成费用',
    make_instrumental TINYINT(1) DEFAULT 0 COMMENT '是否纯音乐',
    request_payload LONGTEXT COMMENT '请求原始载荷(JSON)',
    response_payload LONGTEXT COMMENT '最近一次响应原始载荷(JSON)',
    status VARCHAR(50) DEFAULT 'SUBMITTED' COMMENT '任务状态',
    result_count INT DEFAULT 0 COMMENT '返回clip数量',
    fail_reason TEXT COMMENT '失败原因',
    submit_time DATETIME COMMENT '提交时间',
    start_time DATETIME COMMENT '开始时间',
    finish_time DATETIME COMMENT '完成时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_audio_task_id (task_id),
    UNIQUE KEY uk_audio_vendor_provider_task (vendor, provider_task_id),
    KEY idx_audio_record_user (user_id),
    KEY idx_audio_record_status (status),
    KEY idx_audio_record_created_at (created_at)
) COMMENT='音频生成记录表';

CREATE TABLE IF NOT EXISTS audio_asset (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    generation_record_id BIGINT NOT NULL COMMENT '音频生成记录ID',
    folder_id BIGINT DEFAULT 0 COMMENT '资源文件夹ID，暂默认0',
    clip_id VARCHAR(128) NOT NULL COMMENT '音频clip_id',
    title VARCHAR(255) COMMENT '标题',
    audio_url TEXT COMMENT '音频地址',
    video_url TEXT COMMENT '视频地址',
    cover_url TEXT COMMENT '封面地址',
    prompt TEXT COMMENT '提示词',
    tags VARCHAR(500) COMMENT '风格标签',
    model VARCHAR(100) COMMENT '模型编码',
    source_type VARCHAR(64) COMMENT '来源类型',
    status VARCHAR(50) COMMENT 'clip状态',
    raw_payload LONGTEXT COMMENT 'clip原始信息(JSON)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_audio_clip_id (clip_id),
    KEY idx_audio_asset_user (user_id),
    KEY idx_audio_asset_generation (generation_record_id),
    KEY idx_audio_asset_created_at (created_at)
) COMMENT='音频资产表';
