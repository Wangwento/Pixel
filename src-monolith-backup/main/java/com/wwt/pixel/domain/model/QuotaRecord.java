package com.wwt.pixel.domain.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * 额度变动记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotaRecord {
    private Long id;
    private Long userId;
    private Integer quota;       // 变动额度(正为增加,负为消耗)
    private Integer balance;     // 变动后余额
    private Integer type;        // 类型
    private String source;       // 来源标识
    private String description;  // 描述
    private LocalDateTime createdAt;

    /**
     * 额度变动类型
     */
    public static class Type {
        public static final int GENERATE_CONSUME = 1;  // 生成消耗
        public static final int POINTS_EXCHANGE = 2;   // 积分兑换
        public static final int VIP_GIFT = 3;          // VIP赠送
        public static final int PURCHASE = 4;          // 购买
        public static final int SYSTEM_ADJUST = 5;     // 系统调整
        public static final int DAILY_RESET = 6;       // 每日重置
    }
}