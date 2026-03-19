package com.wwt.pixel.auth.service;

import com.wwt.pixel.common.constant.VerifyCodeConstants;
import com.wwt.pixel.common.exception.BusinessException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 验证码服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyCodeService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${pixel.mail.from:}")
    private String mailFrom;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public void sendEmailCode(String email, String scene) {
        if (!VerifyCodeConstants.isSupportedEmailScene(scene)) {
            throw new BusinessException("不支持的邮箱验证码场景");
        }

        String code = generateCode();
        stringRedisTemplate.opsForValue().set(
                VerifyCodeConstants.buildEmailKey(scene, email),
                code,
                VerifyCodeConstants.CODE_TTL
        );
        printCode("email", scene, email, code);
        sendEmail(email, scene, code);
    }

    public void sendPhoneCode(String phone, String scene) {
        if (!VerifyCodeConstants.isSupportedPhoneScene(scene)) {
            throw new BusinessException("不支持的手机验证码场景");
        }

        String code = generateCode();
        stringRedisTemplate.opsForValue().set(
                VerifyCodeConstants.buildPhoneKey(scene, phone),
                code,
                VerifyCodeConstants.CODE_TTL
        );
        printCode("phone", scene, phone, code);
    }

    public void validateEmailCode(String email, String scene, String code, boolean consumeAfterVerify) {
        validateCode(VerifyCodeConstants.buildEmailKey(scene, email), code, consumeAfterVerify);
    }

    public void validatePhoneCode(String phone, String scene, String code, boolean consumeAfterVerify) {
        validateCode(VerifyCodeConstants.buildPhoneKey(scene, phone), code, consumeAfterVerify);
    }

    private void validateCode(String redisKey, String code, boolean consumeAfterVerify) {
        String cachedCode = stringRedisTemplate.opsForValue().get(redisKey);
        if (!StringUtils.hasText(cachedCode)) {
            throw new BusinessException("验证码已过期，请重新获取");
        }
        if (!cachedCode.equals(code == null ? null : code.trim())) {
            throw new BusinessException("验证码错误");
        }
        if (consumeAfterVerify) {
            stringRedisTemplate.delete(redisKey);
        }
    }

    private String generateCode() {
        int randomCode = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return String.valueOf(randomCode);
    }

    private void printCode(String targetType, String scene, String target, String code) {
        String consoleMessage = String.format(
                "[Pixel VerifyCode] type=%s scene=%s target=%s code=%s expire=%d分钟",
                targetType,
                scene,
                target,
                code,
                VerifyCodeConstants.CODE_TTL_MINUTES
        );
        log.info(consoleMessage);
        System.out.println(consoleMessage);
    }

    private void sendEmail(String email, String scene, String code) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("未配置邮件发送器，已跳过邮件发送: email={}, scene={}", email, scene);
            return;
        }

        String fromAddress = StringUtils.hasText(mailFrom) ? mailFrom : mailUsername;
        if (!StringUtils.hasText(fromAddress)) {
            log.warn("未配置发件邮箱，已跳过邮件发送: email={}, scene={}", email, scene);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    true,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(fromAddress);
            helper.setTo(email);
            helper.setSubject("Pixel 邮箱验证码");
            helper.setText(buildEmailContent(scene, code), true);
            mailSender.send(message);
            log.info("邮箱验证码发送成功: email={}, scene={}", email, scene);
        } catch (MessagingException | MailException e) {
            log.error("邮箱验证码发送失败: email={}, scene={}", email, scene, e);
            throw new BusinessException("邮箱验证码发送失败，请稍后重试");
        }
    }

    private String buildEmailContent(String scene, String code) {
        String sceneText = switch (VerifyCodeConstants.normalizeScene(scene)) {
            case VerifyCodeConstants.SCENE_BIND_EMAIL -> "绑定邮箱";
            case VerifyCodeConstants.SCENE_REGISTER -> "注册账号";
            default -> "邮箱验证";
        };

        return """
                <div style="font-family:Arial,'PingFang SC','Microsoft YaHei',sans-serif;color:#111827;line-height:1.8;">
                  <h2 style="margin:0 0 16px;">Pixel %s 验证码</h2>
                  <p style="margin:0 0 12px;">您好，您的验证码如下：</p>
                  <div style="display:inline-block;padding:10px 18px;background:#111827;color:#ffffff;font-size:24px;font-weight:700;letter-spacing:6px;border-radius:12px;">
                    %s
                  </div>
                  <p style="margin:16px 0 0;">验证码 5 分钟内有效，请勿泄露给他人。</p>
                </div>
                """.formatted(sceneText, code);
    }
}
