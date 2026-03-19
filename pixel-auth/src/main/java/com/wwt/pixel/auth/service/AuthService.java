package com.wwt.pixel.auth.service;

import com.wwt.pixel.auth.domain.User;
import com.wwt.pixel.auth.mapper.UserMapper;
import com.wwt.pixel.common.constant.CommonConstant;
import com.wwt.pixel.common.constant.VerifyCodeConstants;
import com.wwt.pixel.common.exception.BusinessException;
import com.wwt.pixel.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserGrowthEventProducer userGrowthEventProducer;
    private final VerifyCodeService verifyCodeService;
    private final StringRedisTemplate redisTemplate;

    @Value("${pixel.jwt.secret}")
    private String jwtSecret;

    private static final String DEFAULT_AVATAR = "https://pixel-wwt.oss-cn-hangzhou.aliyuncs.com/images/avator.jpeg";
    private static final String USER_TOKEN_KEY_PREFIX = "user:token:";
    private static final long TOKEN_EXPIRE_DAYS = 7;

    /**
     * 用户登录
     */
    public String login(String username, String password) {
        User user = validateLoginCredentials(username, password);

        String token = generateToken(user);
        log.info("用户登录成功: {}", username);
        return token;
    }

    /**
     * 手机验证码登录
     */
    public String loginByPhone(String phone, String code) {
        User user = userMapper.findByPhone(phone);
        if (user == null || !Integer.valueOf(1).equals(user.getPhoneVerified())) {
            throw new BusinessException("该手机号未绑定已验证账号");
        }
        if (user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }

        verifyCodeService.validatePhoneCode(phone, VerifyCodeConstants.SCENE_LOGIN, code, true);

        String token = generateToken(user);
        log.info("手机号登录成功: phone={}, userId={}", phone, user.getId());
        return token;
    }

    /**
     * 发送邮箱验证码
     */
    public void sendEmailCode(String email, String scene, Long currentUserId) {
        String normalizedScene = VerifyCodeConstants.normalizeScene(scene);
        if (!VerifyCodeConstants.isSupportedEmailScene(normalizedScene)) {
            throw new BusinessException("不支持的邮箱验证码场景");
        }

        verifyCodeService.sendEmailCode(email, normalizedScene);
    }

    /**
     * 发送手机验证码
     */
    public void sendPhoneCode(String phone, String scene, Long currentUserId) {
        String normalizedScene = VerifyCodeConstants.normalizeScene(scene);
        if (!VerifyCodeConstants.isSupportedPhoneScene(normalizedScene)) {
            throw new BusinessException("不支持的手机验证码场景");
        }

        if (VerifyCodeConstants.SCENE_LOGIN.equals(normalizedScene)) {
            User user = userMapper.findByPhone(phone);
            if (user == null || !Integer.valueOf(1).equals(user.getPhoneVerified())) {
                throw new BusinessException("该手机号未绑定已验证账号");
            }
        } else if (VerifyCodeConstants.SCENE_BIND_PHONE.equals(normalizedScene)) {
            User existingUser = userMapper.findByPhone(phone);
            if (existingUser != null && !existingUser.getId().equals(currentUserId)) {
                throw new BusinessException("手机号已被其他账号绑定");
            }
        }

        verifyCodeService.sendPhoneCode(phone, normalizedScene);
    }

    /**
     * 用户注册
     */
    @Transactional
    public User register(String username, String password, String email, String emailCode, String inviteCode) {
        // 检查用户名是否存在
        if (userMapper.findByUsername(username) != null) {
            throw new BusinessException("用户名已存在");
        }
        if (userMapper.findByEmail(email) != null) {
            throw new BusinessException("邮箱已被注册");
        }

        verifyCodeService.validateEmailCode(email, VerifyCodeConstants.SCENE_REGISTER, emailCode, true);

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(username);
        user.setAvatar(DEFAULT_AVATAR);
        user.setEmail(email);
        user.setEmailVerified(1);
        user.setPhoneVerified(0);
        user.setRealNameVerified(0);
        user.setPoints(0);
        user.setTotalPoints(0);
        user.setFreeQuota(0);
        user.setFreeQuotaTotal(0);
        user.setDailyLimit(CommonConstant.DAILY_GENERATE_LIMIT);
        user.setDailyUsed(0);
        user.setDailyLimitDate(LocalDate.now());
        user.setMonthlyQuota(0);
        user.setMonthlyQuotaUsed(0);
        user.setUserType(0);
        user.setVipLevel(0);
        user.setLevel(1);
        user.setExp(0);
        user.setStatus(1);
        user.setContinuousSignDays(0);
        user.setProfileCompleted(0);
        user.setInviteCode(generateInviteCode());

        // 处理邀请码
        if (inviteCode != null && !inviteCode.isBlank()) {
            User inviter = userMapper.findByInviteCode(inviteCode);
            if (inviter != null) {
                user.setInvitedBy(inviter.getId());
            }
        }

        userMapper.insert(user);
        publishRegisterEventAfterCommit(user);
        log.info("用户注册成功: {}, userId={}, 新人礼包改为通知手动领取", username, user.getId());
        return user;
    }

    /**
     * 根据ID查询用户
     */
    public User findById(Long id) {
        return userMapper.findById(id);
    }

    /**
     * 根据用户名查询用户
     */
    public User findByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    public User findByPhone(String phone) {
        return userMapper.findByPhone(phone);
    }

    public boolean isEmailAvailable(String email) {
        return userMapper.findByEmail(email) == null;
    }

    public boolean isUsernameAvailable(String username) {
        return userMapper.findByUsername(username) == null;
    }

    public String generateToken(User user) {
        String token = JwtUtil.generateToken(user.getId(), user.getUsername(), jwtSecret);
        redisTemplate.opsForValue().set(buildTokenKey(user.getId()), token, TOKEN_EXPIRE_DAYS, TimeUnit.DAYS);
        return token;
    }

    public void logout(Long userId) {
        redisTemplate.delete(buildTokenKey(userId));
    }

    private User validateLoginCredentials(String username, String password) {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }
        return user;
    }

    private void publishRegisterEventAfterCommit(User user) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            userGrowthEventProducer.sendUserRegisteredEvent(user.getId(), user.getUsername(), user.getInvitedBy());
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                userGrowthEventProducer.sendUserRegisteredEvent(user.getId(), user.getUsername(), user.getInvitedBy());
            }
        });
    }

    private String buildTokenKey(Long userId) {
        return USER_TOKEN_KEY_PREFIX + userId;
    }

    /**
     * 生成唯一邀请码
     */
    private String generateInviteCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

}
