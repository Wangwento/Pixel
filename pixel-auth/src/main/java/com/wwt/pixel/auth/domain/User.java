package com.wwt.pixel.auth.domain;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户实体 (Auth服务只用于登录/注册)
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
    private Integer phoneVerified;
    private Integer realNameVerified;
    private Integer points;
    private Integer totalPoints;
    private Integer freeQuota;
    private Integer freeQuotaTotal;
    private Integer dailyLimit;
    private Integer dailyUsed;
    private LocalDate dailyLimitDate;
    private Integer monthlyQuota;
    private Integer monthlyQuotaUsed;
    private LocalDate monthlyQuotaDate;
    private Integer userType;
    private Integer vipLevel;
    private LocalDateTime vipExpireTime;
    private Integer level;
    private Integer exp;
    private Integer status;
    private LocalDate lastSignDate;
    private Integer continuousSignDays;
    private String inviteCode;
    private Long invitedBy;
    private Integer profileCompleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isVip() {
        if (vipLevel == null || vipLevel == 0) {
            return false;
        }
        return vipExpireTime != null && vipExpireTime.isAfter(LocalDateTime.now());
    }
}