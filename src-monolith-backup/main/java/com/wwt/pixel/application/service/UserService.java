package com.wwt.pixel.application.service;

import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.domain.model.*;
import com.wwt.pixel.infrastructure.persistence.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户服务 (新积分体系: 1张图=100积分=0.2元成本)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PointsRecordMapper pointsRecordMapper;
    private final QuotaRecordMapper quotaRecordMapper;
    private final PasswordEncoder passwordEncoder;

    // ==================== 积分规则配置 (新体系) ====================
    // 核心锚点: 1张图 = 100积分 = 0.2元成本
    // 额度消耗顺序: 1.免费额度 -> 2.购买额度包 -> 3.积分兑换
    public static final int POINTS_PER_IMAGE = 100;              // 生成1张图需要的积分
    public static final int AD_REWARD_POINTS = 30;               // 观看1个广告奖励积分 (需3-4个广告=1张图)
    public static final int REGISTER_GIFT_POINTS = 20;          // 新人礼包积分
    public static final int REGISTER_GIFT_QUOTA = 3;             // 新人礼包免费额度(3次,7天有效)
    public static final int REGISTER_GIFT_EXPIRE_DAYS = 7;       // 新人礼包有效期(天)
    public static final int PROFILE_COMPLETE_POINTS = 50;        // 完善资料奖励 (头像+昵称)
    public static final int REAL_NAME_VERIFY_POINTS = 100;       // 实名认证奖励积分
    public static final int INVITE_REWARD_POINTS = 100;          // 邀请奖励 (双方各100积分)
    public static final int SHARE_REWARD_POINTS = 20;            // 分享作品奖励
    public static final int DAILY_GENERATE_LIMIT = 10;           // 普通用户每日生成上限
    public static final int MONTHLY_QUOTA_LEVEL1 = 100;          // 月卡每月免费额度
    public static final int MONTHLY_QUOTA_LEVEL2 = 120;          // 年卡每月免费额度
    public static final int YEARLY_GIFT_POINTS = 500;            // 年卡购买赠送积分

    // 默认头像URL
    public static final String DEFAULT_AVATAR = "https://pixel-wwt.oss-cn-hangzhou.aliyuncs.com/images/avator.jpeg";

    // ==================== 用户基础操作 ====================

    public User findById(Long id) {
        return userMapper.findById(id);
    }

    public User findByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    /**
     * 用户注册 (新人礼包: 20积分 + 3次免费额度(7天有效))
     */
    @Transactional
    public User register(String username, String password, String email) {
        return register(username, password, email, null);
    }

    /**
     * 用户注册(带邀请码)
     * 新人礼包: 20积分 + 3次免费额度(7天有效期)
     */
    @Transactional
    public User register(String username, String password, String email, String inviteCode) {
        // 检查用户名是否存在
        if (userMapper.findByUsername(username) != null) {
            throw new BusinessException("用户名已存在");
        }
        if (email != null && userMapper.findByEmail(email) != null) {
            throw new BusinessException("邮箱已被注册");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(username);
        user.setAvatar(DEFAULT_AVATAR);              // 默认头像
        user.setEmail(email);
        user.setPhoneVerified(0);                    // 未认证
        user.setRealNameVerified(0);                 // 未认证
        user.setPoints(REGISTER_GIFT_POINTS);       // 新人礼包20积分
        user.setTotalPoints(REGISTER_GIFT_POINTS);
        user.setFreeQuota(REGISTER_GIFT_QUOTA);     // 新人礼包3次免费额度
        user.setFreeQuotaTotal(REGISTER_GIFT_QUOTA);
        user.setDailyLimit(DAILY_GENERATE_LIMIT);   // 每日生成上限10张
        user.setDailyUsed(0);
        user.setDailyLimitDate(LocalDate.now());
        user.setMonthlyQuota(0);                    // 非会员无月度额度
        user.setMonthlyQuotaUsed(0);
        user.setUserType(0);  // 普通用户
        user.setVipLevel(0);
        user.setLevel(1);
        user.setExp(0);
        user.setStatus(1);
        user.setContinuousSignDays(0);
        user.setProfileCompleted(0);                // 资料未完善
        user.setInviteCode(generateInviteCode());   // 生成邀请码

        // 处理邀请码
        if (inviteCode != null && !inviteCode.isBlank()) {
            User inviter = userMapper.findByInviteCode(inviteCode);
            if (inviter != null) {
                user.setInvitedBy(inviter.getId());
            }
        }

        userMapper.insert(user);

        // 记录新人礼包积分
        recordPoints(user.getId(), REGISTER_GIFT_POINTS, user.getPoints(),
                PointsRecord.Type.NEW_USER_GIFT, "register", "新人礼包积分");

        log.info("用户注册成功: {}, 赠送{}积分+{}次免费额度", username, REGISTER_GIFT_POINTS, REGISTER_GIFT_QUOTA);
        return user;
    }

    /**
     * 生成唯一邀请码
     */
    private String generateInviteCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 验证密码
     */
    public boolean verifyPassword(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }

    /**
     * 修改密码
     */
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!verifyPassword(user, oldPassword)) {
            throw new BusinessException("原密码错误");
        }
        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
    }

    // ==================== 签到系统 (新体系: 第1天10分...第7天70分) ====================

    /**
     * 用户签到 (VIP用户积分翻倍)
     */
    @Transactional
    public int signIn(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!user.canSignIn()) {
            throw new BusinessException("今日已签到");
        }

        int earnedPoints = user.doSignIn();

        // 更新签到信息和积分
        userMapper.updateSignIn(userId, user.getLastSignDate(), user.getContinuousSignDays());
        userMapper.updatePoints(userId, user.getPoints(), user.getTotalPoints());

        // 记录积分变动
        String desc = user.isVip()
                ? String.format("签到奖励(连续%d天,VIP翻倍)", user.getContinuousSignDays())
                : String.format("签到奖励(连续%d天)", user.getContinuousSignDays());
        recordPoints(userId, earnedPoints, user.getPoints(),
                PointsRecord.Type.SIGN_IN, "sign_in", desc);

        log.info("用户签到成功: userId={}, points={}, continuous={}, isVip={}",
                userId, earnedPoints, user.getContinuousSignDays(), user.isVip());
        return earnedPoints;
    }

    // ==================== 积分系统 (新体系: 100积分=1张图) ====================

    /**
     * 积分兑换生成额度 (100积分=1张图)
     */
    @Transactional
    public int exchangeQuota(Long userId, int imageCount) {
        if (imageCount <= 0) {
            throw new BusinessException("兑换数量必须大于0");
        }

        int requiredPoints = imageCount * POINTS_PER_IMAGE;
        User user = userMapper.findById(userId);

        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getPoints() < requiredPoints) {
            throw new BusinessException(String.format("积分不足，需要%d积分，当前%d积分", requiredPoints, user.getPoints()));
        }

        // 扣除积分
        user.consumePoints(requiredPoints);
        userMapper.updatePoints(userId, user.getPoints(), user.getTotalPoints());
        recordPoints(userId, -requiredPoints, user.getPoints(),
                PointsRecord.Type.EXCHANGE_QUOTA, "exchange",
                String.format("兑换%d张生成额度", imageCount));

        log.info("积分兑换额度: userId={}, points={}, images={}", userId, requiredPoints, imageCount);
        return imageCount;
    }

    /**
     * 观看广告获得积分 (每次30积分)
     */
    @Transactional
    public int watchAdReward(Long userId, String adId, String adType) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        user.addPoints(AD_REWARD_POINTS);
        userMapper.updatePoints(userId, user.getPoints(), user.getTotalPoints());
        recordPoints(userId, AD_REWARD_POINTS, user.getPoints(),
                PointsRecord.Type.WATCH_AD, adId,
                String.format("观看%s广告奖励", adType));

        log.info("广告奖励: userId={}, adId={}, points={}", userId, adId, AD_REWARD_POINTS);
        return AD_REWARD_POINTS;
    }

    /**
     * 邀请奖励 (邀请人和被邀请人各得100积分，被邀请人完成首图后发放)
     */
    @Transactional
    public void grantInviteReward(Long inviterId, Long inviteeId) {
        // 给邀请人发放奖励
        User inviter = userMapper.findById(inviterId);
        if (inviter != null) {
            inviter.addPoints(INVITE_REWARD_POINTS);
            userMapper.updatePoints(inviterId, inviter.getPoints(), inviter.getTotalPoints());
            recordPoints(inviterId, INVITE_REWARD_POINTS, inviter.getPoints(),
                    PointsRecord.Type.INVITE_REWARD, "invite_" + inviteeId,
                    "邀请好友奖励");
        }

        // 给被邀请人发放奖励
        User invitee = userMapper.findById(inviteeId);
        if (invitee != null) {
            invitee.addPoints(INVITE_REWARD_POINTS);
            userMapper.updatePoints(inviteeId, invitee.getPoints(), invitee.getTotalPoints());
            recordPoints(inviteeId, INVITE_REWARD_POINTS, invitee.getPoints(),
                    PointsRecord.Type.INVITE_REWARD, "invited_by_" + inviterId,
                    "受邀注册奖励");
        }

        log.info("邀请奖励发放: inviter={}, invitee={}, points={}", inviterId, inviteeId, INVITE_REWARD_POINTS);
    }

    /**
     * 分享作品奖励 (每次20积分)
     */
    @Transactional
    public int shareReward(Long userId, String imageId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        user.addPoints(SHARE_REWARD_POINTS);
        userMapper.updatePoints(userId, user.getPoints(), user.getTotalPoints());
        recordPoints(userId, SHARE_REWARD_POINTS, user.getPoints(),
                PointsRecord.Type.SHARE_REWARD, "share_" + imageId,
                "分享作品奖励");

        log.info("分享奖励: userId={}, imageId={}, points={}", userId, imageId, SHARE_REWARD_POINTS);
        return SHARE_REWARD_POINTS;
    }

    /**
     * 完善资料奖励 (头像+昵称, 50积分)
     */
    @Transactional
    public int completeProfileReward(Long userId, String nickname, String avatar) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getProfileCompleted() != null && user.getProfileCompleted() == 1) {
            throw new BusinessException("资料已完善，不可重复领取");
        }
        if (nickname == null || nickname.isBlank() || avatar == null || avatar.isBlank()) {
            throw new BusinessException("请完善昵称和头像");
        }

        // 更新资料
        user.setNickname(nickname);
        user.setAvatar(avatar);
        user.setProfileCompleted(1);
        userMapper.updateProfile(userId, nickname, avatar, 1);

        // 发放奖励
        user.addPoints(PROFILE_COMPLETE_POINTS);
        userMapper.updatePoints(userId, user.getPoints(), user.getTotalPoints());
        recordPoints(userId, PROFILE_COMPLETE_POINTS, user.getPoints(),
                PointsRecord.Type.PROFILE_COMPLETE, "profile",
                "完善资料奖励");

        log.info("完善资料奖励: userId={}, points={}", userId, PROFILE_COMPLETE_POINTS);
        return PROFILE_COMPLETE_POINTS;
    }

    /**
     * 增加积分(通用方法)
     */
    @Transactional
    public void addPoints(Long userId, int points, int type, String source, String description) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        user.addPoints(points);
        userMapper.updatePoints(userId, user.getPoints(), user.getTotalPoints());
        recordPoints(userId, points, user.getPoints(), type, source, description);
    }

    // ==================== 额度系统 (新体系: VIP月度额度 + 积分消耗) ====================

    /**
     * 检查是否可以生成图片并消耗额度
     * 消耗优先级: 1.免费额度(VIP月度/新人礼包) -> 2.购买额度包 -> 3.积分兑换(需主动兑换)
     * 同时检查每日限额(普通用户10张/天)
     */
    @Transactional
    public GenerateCheckResult checkAndConsumeForGenerate(Long userId, String source) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        user.refreshDailyLimitIfNeeded();

        // 1. 检查每日生成限额(防刷)
        if (!user.consumeDailyLimit()) {
            throw new BusinessException(String.format("今日已达生成上限(%d张)，明天再来吧", user.getDailyLimit()));
        }

        GenerateCheckResult result = new GenerateCheckResult();
        result.setUserId(userId);

        // 2. 优先消耗VIP月度免费额度
        if (user.isVip()) {
            int consumed = user.consumeMonthlyQuota(1);
            if (consumed > 0) {
                userMapper.updateMonthlyQuota(userId, user.getMonthlyQuotaUsed(), user.getMonthlyQuotaDate());
                userMapper.updateDailyLimit(userId, user.getDailyUsed(), user.getDailyLimitDate());
                result.setConsumeType("vip_monthly");
                result.setPointsConsumed(0);
                log.info("VIP月度额度消耗: userId={}, remaining={}", userId, user.getAvailableMonthlyQuota());
                return result;
            }
        }

        // 3. 消耗免费额度(新人礼包等)
        int freeConsumed = user.consumeFreeQuota(1);
        if (freeConsumed > 0) {
            userMapper.updateFreeQuota(userId, user.getFreeQuota(), user.getFreeQuotaTotal());
            userMapper.updateDailyLimit(userId, user.getDailyUsed(), user.getDailyLimitDate());
            result.setConsumeType("free_quota");
            result.setPointsConsumed(0);
            log.info("免费额度消耗: userId={}, remaining={}", userId, user.getFreeQuota());
            return result;
        }

        // 4. 消耗购买的额度包(由QuotaService处理)
        // 这里暂时跳过，需要调用quotaService.consumeQuota

        // 5. 积分不自动消耗，需要用户主动兑换额度
        // 回滚每日限额
        user.setDailyUsed(user.getDailyUsed() - 1);
        throw new BusinessException("额度不足，请购买额度包或使用积分兑换");
    }

    /**
     * 获取用户额度信息 (新体系)
     */
    public UserQuotaInfo getQuotaInfo(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            return null;
        }
        user.refreshDailyLimitIfNeeded();
        user.refreshMonthlyQuotaIfNeeded();

        UserQuotaInfo info = new UserQuotaInfo();
        info.setPoints(user.getPoints());
        info.setPointsPerImage(POINTS_PER_IMAGE);
        info.setFreeQuota(user.getFreeQuota());              // 免费额度(新人礼包等)
        info.setMonthlyQuota(user.getMonthlyQuota());        // VIP月度免费额度
        info.setMonthlyUsed(user.getMonthlyQuotaUsed());
        info.setMonthlyRemaining(user.getAvailableMonthlyQuota());
        info.setDailyLimit(user.getDailyLimit());            // 每日生成上限(防刷)
        info.setDailyUsed(user.getDailyUsed());
        info.setDailyRemaining(user.getAvailableDailyLimit());
        info.setVip(user.isVip());
        info.setVipLevel(user.getVipLevel());
        // 总可用额度: 免费额度 + VIP月度剩余 (不含积分，积分需主动兑换)
        int totalAvailable = (user.getFreeQuota() != null ? user.getFreeQuota() : 0)
                + user.getAvailableMonthlyQuota();
        info.setTotalAvailable(totalAvailable);
        // 今日可生成数量(考虑每日限额)
        info.setCanGenerateCount(Math.min(totalAvailable, user.getAvailableDailyLimit()));
        return info;
    }

    // ==================== 记录方法 ====================

    private void recordPoints(Long userId, int points, int balance, int type, String source, String description) {
        PointsRecord record = PointsRecord.builder()
                .userId(userId)
                .points(points)
                .balance(balance)
                .type(type)
                .source(source)
                .description(description)
                .build();
        pointsRecordMapper.insert(record);
    }

    private void recordQuota(Long userId, int quota, int balance, int type, String source, String description) {
        QuotaRecord record = QuotaRecord.builder()
                .userId(userId)
                .quota(quota)
                .balance(balance)
                .type(type)
                .source(source)
                .description(description)
                .build();
        quotaRecordMapper.insert(record);
    }

    /**
     * 获取积分记录
     */
    public List<PointsRecord> getPointsRecords(Long userId, int page, int size) {
        return pointsRecordMapper.findByUserId(userId, (page - 1) * size, size);
    }

    /**
     * 获取额度记录
     */
    public List<QuotaRecord> getQuotaRecords(Long userId, int page, int size) {
        return quotaRecordMapper.findByUserId(userId, (page - 1) * size, size);
    }

    // ==================== 内部DTO ====================

    /**
     * 用户额度信息 (新体系)
     * 额度消耗顺序: 免费额度 -> 购买额度包 -> 积分兑换
     */
    @lombok.Data
    public static class UserQuotaInfo {
        private Integer points;              // 积分余额
        private Integer pointsPerImage;      // 生成1张图需要的积分(100)
        private Integer freeQuota;           // 免费额度(新人礼包/活动赠送等)
        private Integer monthlyQuota;        // VIP月度免费额度(月卡100/年卡120)
        private Integer monthlyUsed;         // 本月已用VIP额度
        private Integer monthlyRemaining;    // 本月剩余VIP额度
        private Integer totalAvailable;      // 总可用免费额度(免费+VIP月度)
        private Integer dailyLimit;          // 每日生成上限(防刷, 10张)
        private Integer dailyUsed;           // 今日已生成
        private Integer dailyRemaining;      // 今日剩余可生成
        private Boolean vip;                 // 是否VIP
        private Integer vipLevel;            // VIP等级: 0-无, 1-月卡, 2-年卡
        private Integer canGenerateCount;    // 当前可生成图片数(考虑每日限额)
    }

    /**
     * 生成消耗检查结果
     */
    @lombok.Data
    public static class GenerateCheckResult {
        private Long userId;
        private String consumeType;          // vip_monthly | free_quota | quota_package
        private Integer pointsConsumed;      // 消耗的积分(使用免费额度则为0)
    }
}
