package com.wwt.pixel.domain.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * 邀请记录 (邀请奖励: 双方各100积分)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteRecord {
    private Long id;
    private Long inviterId;          // 邀请人ID
    private Long inviteeId;          // 被邀请人ID
    private String inviteCode;       // 邀请码
    private Integer status;          // 状态: 0-已注册, 1-已完成首图(双方领奖)
    private Integer inviterReward;   // 邀请人已领取奖励积分
    private Integer inviteeReward;   // 被邀请人已领取奖励积分
    private LocalDateTime createdAt;
    private LocalDateTime completedAt; // 完成时间(首图生成)

    /**
     * 状态常量
     */
    public static class Status {
        public static final int REGISTERED = 0;    // 已注册
        public static final int COMPLETED = 1;     // 已完成首图，双方已领奖
    }

    /**
     * 是否已完成(首图生成)
     */
    public boolean isCompleted() {
        return status != null && status == Status.COMPLETED;
    }
}
