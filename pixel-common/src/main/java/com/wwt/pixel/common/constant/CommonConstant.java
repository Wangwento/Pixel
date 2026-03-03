package com.wwt.pixel.common.constant;

/**
 * 通用常量
 */
public class CommonConstant {

    /**
     * HTTP Header 中传递的用户信息
     */
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String HEADER_TOKEN = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 积分规则 (核心锚点: 1张图=100积分=0.2元成本)
     */
    public static final int POINTS_PER_IMAGE = 100;         // 生成1张图需要的积分
    public static final int AD_REWARD_POINTS = 30;          // 观看广告奖励积分
    public static final int REGISTER_GIFT_POINTS = 20;      // 新人礼包积分
    public static final int REGISTER_GIFT_QUOTA = 3;        // 新人礼包免费额度
    public static final int DAILY_GENERATE_LIMIT = 10;      // 每日生成上限
    public static final int INVITE_REWARD_POINTS = 100;     // 邀请奖励积分
    public static final int SHARE_REWARD_POINTS = 20;       // 分享奖励积分
    public static final int PROFILE_COMPLETE_POINTS = 50;   // 完善资料奖励
    public static final int REAL_NAME_VERIFY_POINTS = 100;  // 实名认证奖励

    /**
     * VIP配置
     */
    public static final int VIP_MONTHLY_QUOTA_L1 = 100;     // 月卡每月额度
    public static final int VIP_MONTHLY_QUOTA_L2 = 120;     // 年卡每月额度
}
