package com.wwt.pixel.domain.model;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户实体 (新积分体系: 1张图=100积分=0.2元成本)
 *
 * 额度消耗顺序:
 * 1. 免费额度(VIP月度赠送、新人礼包等) - 优先消耗
 * 2. 购买额度包(付费购买) - 其次消耗
 * 3. 积分兑换(100积分=1张) - 用户主动兑换
 */
@Data
public class User {
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;

    // 认证状态
    private Integer phoneVerified;    // 0-未认证, 1-已认证
    private String realName;          // 真实姓名
    private String idCard;            // 身份证号(加密)
    private Integer realNameVerified; // 0-未认证, 1-审核中, 2-已认证, 3-失败

    // ==================== 积分系统 ====================
    // 核心: 生成1张图=100积分, 用户可主动用积分兑换额度
    private Integer points;           // 积分余额
    private Integer totalPoints;      // 累计获得积分

    // ==================== 免费额度(优先消耗) ====================
    // 包括: 新人礼包、VIP月度赠送、活动赠送等
    private Integer freeQuota;        // 免费额度余额
    private Integer freeQuotaTotal;   // 免费额度总计(用于统计)

    // 每日生成限制(所有用户，防刷)
    private Integer dailyLimit;       // 每日生成上限(普通用户10张)
    private Integer dailyUsed;        // 今日已生成数量
    private LocalDate dailyLimitDate; // 限制刷新日期

    // VIP月度免费额度
    private Integer monthlyQuota;     // VIP月度免费额度(月卡100张/年卡120张)
    private Integer monthlyQuotaUsed; // 本月已用VIP额度
    private LocalDate monthlyQuotaDate; // 月度额度刷新日期

    // ==================== VIP系统 ====================
    private Integer userType;         // 0-普通, 1-VIP
    private Integer vipLevel;         // 0-无, 1-月卡, 2-年卡
    private LocalDateTime vipExpireTime;

    // 成长系统
    private Integer level;            // 用户等级
    private Integer exp;              // 当前经验值

    // 签到 (新体系: 第1天10分,连续递增,第7天最高70分)
    private LocalDate lastSignDate;
    private Integer continuousSignDays;

    // 邀请系统
    private String inviteCode;        // 我的邀请码
    private Long invitedBy;           // 邀请人ID

    // 资料完善
    private Integer profileCompleted; // 0-未完善, 1-已完善(头像+昵称)

    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 认证状态常量
     */
    public static class VerifyStatus {
        public static final int NOT_VERIFIED = 0;  // 未认证
        public static final int PENDING = 1;       // 审核中
        public static final int VERIFIED = 2;      // 已认证
        public static final int FAILED = 3;        // 认证失败
    }

    /**
     * 检查手机是否已认证
     */
    public boolean hasPhoneVerified() {
        return phoneVerified != null && phoneVerified == 1;
    }

    /**
     * 检查实名是否已认证
     */
    public boolean hasRealNameVerified() {
        return realNameVerified != null && realNameVerified == VerifyStatus.VERIFIED;
    }

    /**
     * 是否是VIP用户
     */
    public boolean isVip() {
        if (vipLevel == null || vipLevel == 0) {
            return false;
        }
        return vipExpireTime != null && vipExpireTime.isAfter(LocalDateTime.now());
    }

    /**
     * 获取今日剩余生成次数
     */
    public int getAvailableDailyLimit() {
        refreshDailyLimitIfNeeded();
        return Math.max(0, dailyLimit - dailyUsed);
    }

    /**
     * 检查并刷新每日限制
     */
    public void refreshDailyLimitIfNeeded() {
        LocalDate today = LocalDate.now();
        if (dailyLimitDate == null || !dailyLimitDate.equals(today)) {
            dailyUsed = 0;
            dailyLimitDate = today;
        }
    }

    /**
     * 检查并刷新月度会员额度
     */
    public void refreshMonthlyQuotaIfNeeded() {
        if (!isVip()) {
            return;
        }
        LocalDate today = LocalDate.now();
        if (monthlyQuotaDate == null || !monthlyQuotaDate.getMonth().equals(today.getMonth())
            || monthlyQuotaDate.getYear() != today.getYear()) {
            monthlyQuotaUsed = 0;
            monthlyQuotaDate = today;
            // 根据VIP等级设置月度额度
            monthlyQuota = (vipLevel == 2) ? 120 : 100; // 年卡120张,月卡100张
        }
    }

    /**
     * 获取可用月度会员额度(VIP免费额度)
     */
    public int getAvailableMonthlyQuota() {
        if (!isVip()) {
            return 0;
        }
        refreshMonthlyQuotaIfNeeded();
        return Math.max(0, monthlyQuota - monthlyQuotaUsed);
    }

    /**
     * 检查并消耗每日限额(防刷限制)
     * @return 是否有剩余限额
     */
    public boolean consumeDailyLimit() {
        refreshDailyLimitIfNeeded();
        if (dailyUsed >= dailyLimit) {
            return false;
        }
        dailyUsed++;
        return true;
    }

    /**
     * 消耗月度会员免费额度
     * @return 实际消耗的额度(0表示无可用额度)
     */
    public int consumeMonthlyQuota(int amount) {
        if (!isVip()) {
            return 0;
        }
        refreshMonthlyQuotaIfNeeded();
        int available = monthlyQuota - monthlyQuotaUsed;
        int actual = Math.min(amount, available);
        monthlyQuotaUsed += actual;
        return actual;
    }

    /**
     * 消耗免费额度
     * @return 实际消耗的额度
     */
    public int consumeFreeQuota(int amount) {
        if (freeQuota == null || freeQuota <= 0) {
            return 0;
        }
        int actual = Math.min(amount, freeQuota);
        freeQuota -= actual;
        return actual;
    }

    /**
     * 增加免费额度
     */
    public void addFreeQuota(int amount) {
        if (freeQuota == null) {
            freeQuota = 0;
        }
        if (freeQuotaTotal == null) {
            freeQuotaTotal = 0;
        }
        freeQuota += amount;
        freeQuotaTotal += amount;
    }

    /**
     * 获取总可用额度 (免费额度 + VIP月度额度)
     */
    public int getTotalAvailableQuota() {
        int total = (freeQuota != null ? freeQuota : 0);
        total += getAvailableMonthlyQuota();
        return total;
    }

    /**
     * 增加积分
     */
    public void addPoints(int amount) {
        if (amount > 0) {
            this.points += amount;
            this.totalPoints += amount;
        }
    }

    /**
     * 消耗积分
     */
    public boolean consumePoints(int amount) {
        if (this.points >= amount) {
            this.points -= amount;
            return true;
        }
        return false;
    }

    /**
     * 检查是否可以签到
     */
    public boolean canSignIn() {
        LocalDate today = LocalDate.now();
        return lastSignDate == null || !lastSignDate.equals(today);
    }

    /**
     * 执行签到 (新体系: 第1天10分,连续递增,第7天最高70分)
     * @return 获得的积分
     */
    public int doSignIn() {
        if (!canSignIn()) {
            return 0;
        }
        LocalDate today = LocalDate.now();

        // 计算连续签到
        if (lastSignDate != null && lastSignDate.equals(today.minusDays(1))) {
            continuousSignDays = Math.min(continuousSignDays + 1, 7); // 最多7天循环
        } else {
            continuousSignDays = 1;
        }
        lastSignDate = today;

        // 签到积分: 第1天10分，第2天20分...第7天70分
        int earnedPoints = continuousSignDays * 10;

        // VIP用户积分翻倍
        if (isVip()) {
            earnedPoints *= 2;
        }

        addPoints(earnedPoints);
        return earnedPoints;
    }

    /**
     * 获取签到积分倍率
     */
    public int getSignInMultiplier() {
        return isVip() ? 2 : 1;
    }
}
