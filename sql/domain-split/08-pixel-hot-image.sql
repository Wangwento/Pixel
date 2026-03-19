-- ============================================================
-- 热门内容表 (hot_image)
-- 数据库: pixel_image
-- 用途: 用户提交图片/视频申请上热门，管理员审核后展示，通过可领取积分奖励
-- ============================================================

USE pixel_image;

CREATE TABLE IF NOT EXISTS `hot_image` (
  `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`         BIGINT NOT NULL COMMENT '提交用户ID',
  `image_asset_id`  BIGINT COMMENT '关联的图片资产ID',
  `video_asset_id`  BIGINT COMMENT '关联的视频资产ID(逻辑引用 pixel_vedio.video_asset.id)',
  `image_url`       VARCHAR(512) NOT NULL COMMENT '媒体资源URL(图片URL或视频URL)',
  `media_type`      VARCHAR(16) NOT NULL DEFAULT 'image' COMMENT '媒体类型: image, video',
  `cover_url`       VARCHAR(512) COMMENT '视频封面URL (仅video类型)',
  `title`           VARCHAR(100) COMMENT '标题',
  `description`     VARCHAR(500) COMMENT '描述',
  `status`          TINYINT DEFAULT 0 COMMENT '0=待审核, 1=已通过, 2=已拒绝, 3=已下架',
  `reject_reason`   VARCHAR(200) COMMENT '拒绝原因',
  `reviewer_id`     BIGINT COMMENT '审核人ID',
  `reviewed_at`     DATETIME COMMENT '审核时间',
  `reward_claimed`  TINYINT DEFAULT 0 COMMENT '0=未领取, 1=已领取 (仅status=1时有效)',
  `reward_points`   INT DEFAULT 100 COMMENT '奖励积分数',
  `like_count`      INT DEFAULT 0 COMMENT '点赞数',
  `collect_count`   INT DEFAULT 0 COMMENT '收藏数',
  `comment_count`   INT DEFAULT 0 COMMENT '评论数',
  `claimed_at`      DATETIME COMMENT '领取时间',
  `created_at`      DATETIME DEFAULT NOW() COMMENT '创建时间',
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_created_at` (`created_at`),
  INDEX `idx_user_image_asset_status` (`user_id`, `image_asset_id`, `status`),
  INDEX `idx_user_video_asset_status` (`user_id`, `video_asset_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='热门内容';
