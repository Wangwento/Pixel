package com.wwt.pixel.domain.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 额度包(带有效期)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotaPackage {
    private Long id;
    private Long userId;
    private Integer quotaTotal;      // 总额度
    private Integer quotaUsed;       // 已用额度
    private Integer quotaRemaining;  // 剩余额度
    private Integer source;          // 来源
    private String sourceDesc;       // 来源描述
    private LocalDateTime expireTime;// 过期时间
    private Integer status;          // 0-已失效, 1-有效, 2-已用完
    private LocalDateTime createdAt;

    /**
     * 额度来源
     */
    public static class Source {
        public static final int REGISTER = 1;       // 注册赠送
        public static final int PHONE_VERIFY = 2;   // 手机认证
        public static final int REAL_NAME_VERIFY = 3; // 实名认证
        public static final int PURCHASE = 4;       // 购买
        public static final int POINTS_EXCHANGE = 5; // 积分兑换
        public static final int VIP_GIFT = 6;       // VIP赠送
        public static final int ACTIVITY = 7;       // 活动赠送
    }

    /**
     * 状态
     */
    public static class Status {
        public static final int EXPIRED = 0;    // 已失效
        public static final int ACTIVE = 1;     // 有效
        public static final int USED_UP = 2;    // 已用完
    }

    /**
     * 是否有效
     */
    public boolean isValid() {
        return status == Status.ACTIVE
            && quotaRemaining > 0
            && expireTime.isAfter(LocalDateTime.now());
    }

    /**
     * 消耗额度
     * @return 实际消耗的额度
     */
    public int consume(int amount) {
        if (!isValid()) {
            return 0;
        }
        int actual = Math.min(amount, quotaRemaining);
        quotaUsed += actual;
        quotaRemaining -= actual;
        if (quotaRemaining == 0) {
            status = Status.USED_UP;
        }
        return actual;
    }
}