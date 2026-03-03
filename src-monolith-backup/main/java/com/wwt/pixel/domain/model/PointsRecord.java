package com.wwt.pixel.domain.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * 积分变动记录 (新积分体系: 1张图=100积分)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointsRecord {
    private Long id;
    private Long userId;
    private Integer points;      // 变动积分(正为增加,负为消耗)
    private Integer balance;     // 变动后余额
    private Integer type;        // 类型
    private String source;       // 来源标识
    private String description;  // 描述
    private LocalDateTime createdAt;

    /**
     * 积分变动类型
     */
    public static class Type {
        public static final int SIGN_IN = 1;           // 签到 (第1天10分...第7天70分)
        public static final int TASK_REWARD = 2;       // 任务奖励
        public static final int EXCHANGE_QUOTA = 3;    // 兑换额度 (100积分=1张图)
        public static final int RECHARGE = 4;          // 充值
        public static final int REFUND = 5;            // 退款
        public static final int SYSTEM_ADJUST = 6;     // 系统调整
        public static final int WATCH_AD = 7;          // 观看广告 (每次30积分)
        public static final int INVITE_REWARD = 8;     // 邀请奖励 (双方各100积分)
        public static final int SHARE_REWARD = 9;      // 分享奖励 (每次20积分)
        public static final int PROFILE_COMPLETE = 10; // 完善资料 (头像+昵称, 50积分)
        public static final int NEW_USER_GIFT = 11;    // 新人礼包 (注册200积分)
    }

    /**
     * 获取类型描述
     */
    public static String getTypeDesc(int type) {
        return switch (type) {
            case Type.SIGN_IN -> "签到奖励";
            case Type.TASK_REWARD -> "任务奖励";
            case Type.EXCHANGE_QUOTA -> "兑换额度";
            case Type.RECHARGE -> "充值";
            case Type.REFUND -> "退款";
            case Type.SYSTEM_ADJUST -> "系统调整";
            case Type.WATCH_AD -> "观看广告";
            case Type.INVITE_REWARD -> "邀请奖励";
            case Type.SHARE_REWARD -> "分享奖励";
            case Type.PROFILE_COMPLETE -> "完善资料";
            case Type.NEW_USER_GIFT -> "新人礼包";
            default -> "未知";
        };
    }
}