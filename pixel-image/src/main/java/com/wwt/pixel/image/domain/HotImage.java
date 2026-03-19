package com.wwt.pixel.image.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 热门内容 (图片/视频)
 */
@Data
public class HotImage {
    private Long id;
    private Long userId;
    private Long imageAssetId;
    private Long videoAssetId;
    private String imageUrl;
    private String mediaType;        // image | video
    private String coverUrl;         // 视频封面 (仅video)
    private String title;
    private String description;
    private Integer status;          // 0=待审核, 1=已通过, 2=已拒绝
    private String rejectReason;
    private Long reviewerId;
    private LocalDateTime reviewedAt;
    private Integer rewardClaimed;   // 0=未领取, 1=已领取
    private Integer rewardPoints;
    private Integer likeCount;       // 点赞数
    private Integer collectCount;    // 收藏数
    private Integer commentCount;    // 评论数
    private LocalDateTime claimedAt;
    private LocalDateTime createdAt;
    private String nickname;

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_REJECTED = 2;
    public static final int STATUS_OFFLINE = 3;

    public static final int REWARD_NOT_CLAIMED = 0;
    public static final int REWARD_CLAIMED = 1;
}
