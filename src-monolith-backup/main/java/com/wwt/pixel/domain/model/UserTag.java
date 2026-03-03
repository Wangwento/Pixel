package com.wwt.pixel.domain.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户标签关联(带权重)
 */
@Data
public class UserTag {
    private Long id;
    private Long userId;
    private Long tagId;
    private BigDecimal weight;   // 权重 0.00-100.00
    private Integer source;      // 1-用户选择, 2-行为分析, 3-系统推断
    private Integer useCount;    // 使用次数
    private LocalDateTime lastUseTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 关联的标签信息(查询时填充)
    private Tag tag;

    /**
     * 增加使用次数并更新权重
     */
    public void incrementUse() {
        this.useCount++;
        this.lastUseTime = LocalDateTime.now();
        // 权重计算: 基础权重 + 使用次数加成(最高50分)
        BigDecimal baseWeight = source == 1 ? new BigDecimal("30") : new BigDecimal("10");
        BigDecimal useBonus = new BigDecimal(Math.min(useCount * 2, 50));
        this.weight = baseWeight.add(useBonus);
    }
}
