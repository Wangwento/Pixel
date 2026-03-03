package com.wwt.pixel.application.service;

import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.domain.model.QuotaPackage;
import com.wwt.pixel.domain.model.QuotaRecord;
import com.wwt.pixel.domain.model.User;
import com.wwt.pixel.infrastructure.persistence.mapper.QuotaPackageMapper;
import com.wwt.pixel.infrastructure.persistence.mapper.QuotaRecordMapper;
import com.wwt.pixel.infrastructure.persistence.mapper.UserMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 额度服务(带有效期的额度包)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final UserMapper userMapper;
    private final QuotaPackageMapper quotaPackageMapper;
    private final QuotaRecordMapper quotaRecordMapper;

    // ==================== 额度配置 ====================
    // TODO: 后期可抽取到配置中心
    public static final int PHONE_VERIFY_QUOTA = 3;          // 手机认证赠送额度
    public static final int PHONE_VERIFY_EXPIRE_DAYS = 7;    // 手机认证额度有效期(天)
    public static final int REAL_NAME_VERIFY_QUOTA = 5;      // 实名认证赠送额度
    public static final int REAL_NAME_VERIFY_EXPIRE_DAYS = 7;// 实名认证额度有效期(天)
    public static final int POINTS_EXCHANGE_EXPIRE_DAYS = 30;// 积分兑换额度有效期(天)
    public static final int PURCHASE_EXPIRE_DAYS = 365;      // 购买额度有效期(天)

    // ==================== 额度查询 ====================

    /**
     * 获取用户额度汇总信息
     * 额度消耗顺序: 免费额度 -> 购买额度包 -> 积分兑换
     */
    public QuotaInfo getQuotaInfo(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            return null;
        }
        user.refreshDailyLimitIfNeeded();
        user.refreshMonthlyQuotaIfNeeded();

        // 统计购买额度包总额度
        int packageQuota = quotaPackageMapper.sumValidQuota(userId);

        QuotaInfo info = new QuotaInfo();
        info.setFreeQuota(user.getFreeQuota());         // 免费额度(新人礼包等)
        info.setMonthlyQuota(user.getAvailableMonthlyQuota()); // VIP月度免费额度
        info.setPackageQuota(packageQuota);             // 购买额度包余额
        info.setDailyLimit(user.getDailyLimit());       // 每日生成上限(防刷)
        info.setDailyUsed(user.getDailyUsed());
        info.setDailyRemaining(user.getAvailableDailyLimit());
        info.setTotalAvailable(user.getTotalAvailableQuota() + packageQuota);
        info.setPhoneVerified(user.hasPhoneVerified());
        info.setRealNameVerified(user.hasRealNameVerified());

        // 未领取的认证奖励
        info.setPhoneVerifyRewardClaimed(
            quotaPackageMapper.countByUserIdAndSource(userId, QuotaPackage.Source.PHONE_VERIFY) > 0);
        info.setRealNameVerifyRewardClaimed(
            quotaPackageMapper.countByUserIdAndSource(userId, QuotaPackage.Source.REAL_NAME_VERIFY) > 0);

        return info;
    }

    /**
     * 获取用户额度包列表
     */
    public List<QuotaPackage> getQuotaPackages(Long userId, int page, int size) {
        return quotaPackageMapper.findByUserId(userId, (page - 1) * size, size);
    }

    // ==================== 额度消耗 ====================

    /**
     * 消耗额度(生成图片时调用) - 使用额度包模式
     * 消耗优先级: 快过期的额度包优先
     */
    @Transactional
    public boolean consumeQuota(Long userId, int amount, String source) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        int remaining = amount;

        // 消耗额度包(按过期时间优先)
        List<QuotaPackage> packages = quotaPackageMapper.findValidByUserId(userId);
        for (QuotaPackage pkg : packages) {
            if (remaining <= 0) break;
            int consumed = pkg.consume(remaining);
            if (consumed > 0) {
                remaining -= consumed;
                quotaPackageMapper.update(pkg);
            }
        }

        if (remaining > 0) {
            throw new BusinessException("额度不足，请完成认证领取额度或购买额度包");
        }

        // 记录额度变动
        int totalBalance = quotaPackageMapper.sumValidQuota(userId);
        recordQuota(userId, -amount, totalBalance, QuotaRecord.Type.GENERATE_CONSUME, source, "生成图片消耗");

        log.info("消耗额度成功: userId={}, amount={}, source={}", userId, amount, source);
        return true;
    }

    // ==================== 认证奖励 ====================

    /**
     * 领取手机认证额度奖励
     */
    @Transactional
    public QuotaPackage claimPhoneVerifyReward(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!user.hasPhoneVerified()) {
            throw new BusinessException("请先完成手机认证");
        }
        if (quotaPackageMapper.countByUserIdAndSource(userId, QuotaPackage.Source.PHONE_VERIFY) > 0) {
            throw new BusinessException("手机认证奖励已领取");
        }

        QuotaPackage pkg = createQuotaPackage(
            userId,
            PHONE_VERIFY_QUOTA,
            QuotaPackage.Source.PHONE_VERIFY,
            "手机认证奖励",
            PHONE_VERIFY_EXPIRE_DAYS
        );

        log.info("领取手机认证奖励: userId={}, quota={}", userId, PHONE_VERIFY_QUOTA);
        return pkg;
    }

    /**
     * 领取实名认证额度奖励
     */
    @Transactional
    public QuotaPackage claimRealNameVerifyReward(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!user.hasRealNameVerified()) {
            throw new BusinessException("请先完成实名认证");
        }
        if (quotaPackageMapper.countByUserIdAndSource(userId, QuotaPackage.Source.REAL_NAME_VERIFY) > 0) {
            throw new BusinessException("实名认证奖励已领取");
        }

        QuotaPackage pkg = createQuotaPackage(
            userId,
            REAL_NAME_VERIFY_QUOTA,
            QuotaPackage.Source.REAL_NAME_VERIFY,
            "实名认证奖励",
            REAL_NAME_VERIFY_EXPIRE_DAYS
        );

        log.info("领取实名认证奖励: userId={}, quota={}", userId, REAL_NAME_VERIFY_QUOTA);
        return pkg;
    }

    // ==================== 积分兑换 ====================

    /**
     * 积分兑换额度
     */
    @Transactional
    public QuotaPackage exchangeWithPoints(Long userId, int quotaCount, int pointsCost) {
        QuotaPackage pkg = createQuotaPackage(
            userId,
            quotaCount,
            QuotaPackage.Source.POINTS_EXCHANGE,
            String.format("积分兑换(%d积分)", pointsCost),
            POINTS_EXCHANGE_EXPIRE_DAYS
        );

        log.info("积分兑换额度: userId={}, quota={}, points={}", userId, quotaCount, pointsCost);
        return pkg;
    }

    // ==================== 内部方法 ====================

    /**
     * 创建额度包
     */
    private QuotaPackage createQuotaPackage(Long userId, int quota, int source,
                                            String sourceDesc, int expireDays) {
        QuotaPackage pkg = QuotaPackage.builder()
            .userId(userId)
            .quotaTotal(quota)
            .quotaUsed(0)
            .quotaRemaining(quota)
            .source(source)
            .sourceDesc(sourceDesc)
            .expireTime(LocalDateTime.now().plusDays(expireDays))
            .status(QuotaPackage.Status.ACTIVE)
            .build();

        quotaPackageMapper.insert(pkg);

        // 记录额度变动
        int totalBalance = quotaPackageMapper.sumValidQuota(userId);
        recordQuota(userId, quota, totalBalance, mapSourceToRecordType(source), "quota_package_" + pkg.getId(), sourceDesc);

        return pkg;
    }

    private int mapSourceToRecordType(int source) {
        return switch (source) {
            case QuotaPackage.Source.PHONE_VERIFY, QuotaPackage.Source.REAL_NAME_VERIFY -> QuotaRecord.Type.SYSTEM_ADJUST;
            case QuotaPackage.Source.PURCHASE -> QuotaRecord.Type.PURCHASE;
            case QuotaPackage.Source.POINTS_EXCHANGE -> QuotaRecord.Type.POINTS_EXCHANGE;
            case QuotaPackage.Source.VIP_GIFT -> QuotaRecord.Type.VIP_GIFT;
            default -> QuotaRecord.Type.SYSTEM_ADJUST;
        };
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

    // ==================== 定时任务 ====================

    /**
     * 定时过期额度包(每小时执行)
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void expireQuotaPackagesJob() {
        int count = quotaPackageMapper.expireQuotaPackages();
        if (count > 0) {
            log.info("过期额度包数量: {}", count);
        }
    }

    // ==================== DTO ====================

    @Data
    public static class QuotaInfo {
        private Integer freeQuota;              // 免费额度(新人礼包等)
        private Integer monthlyQuota;           // VIP月度免费额度
        private Integer packageQuota;           // 购买额度包余额
        private Integer dailyLimit;             // 每日生成上限(防刷)
        private Integer dailyUsed;              // 今日已用
        private Integer dailyRemaining;         // 今日剩余
        private Integer totalAvailable;         // 总可用(免费+VIP+额度包)
        private Boolean phoneVerified;          // 手机是否认证
        private Boolean realNameVerified;       // 实名是否认证
        private Boolean phoneVerifyRewardClaimed;    // 手机认证奖励是否已领
        private Boolean realNameVerifyRewardClaimed; // 实名认证奖励是否已领
    }
}