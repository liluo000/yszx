package com.yunshuzhixue.modules.account.service;

import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.web.ClientIps;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 图形验证与短信验证码（需求 5.1.1，S-ACC-01、02）。
 *
 * <p>本地开发不接真实短信网关（架构 T05 中短信厂商待定），验证码固定为 123456 并记录日志，
 * 图形验证答案固定为 1234；接入 infra/sms 适配器后替换 issueSmsCode 即可，其余限流与校验逻辑不变。</p>
 */
@Service
public class StudentVerificationService {

    public static final String DEV_SMS_CODE = "123456";
    private static final String DEV_CAPTCHA_CODE = "1234";
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);
    private static final Duration SMS_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    private static final int SMS_MAX_WRONG = 5;
    private static final int PHONE_DAILY_LIMIT = 10;
    private static final int IP_HOURLY_LIMIT = 30;
    private static final int IP_DAILY_LIMIT = 100;

    private final StringRedisTemplate redis;

    public StudentVerificationService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public record Captcha(String captchaId, String imageBase64, int expiresIn) {
    }

    /** 生成图形验证：答案存 Redis，验证一次即失效 */
    public Captcha createCaptcha() {
        String id = UUID.randomUUID().toString();
        redis.opsForValue().set("account:captcha:" + id, DEV_CAPTCHA_CODE, CAPTCHA_TTL);
        try {
            BufferedImage image = new BufferedImage(120, 40, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 120, 40);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
            g.setColor(new Color(20, 40, 70));
            g.drawString(DEV_CAPTCHA_CODE, 30, 28);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return new Captcha(id, Base64.getEncoder().encodeToString(out.toByteArray()), 300);
        } catch (Exception e) {
            throw new IllegalStateException("生成图形验证失败", e);
        }
    }

    /** 发送短信验证码：先核销图形验证，再按手机号与 IP 限流 */
    public int sendSmsCode(String phone, String scene, String captchaId, String captchaCode) {
        String captchaKey = "account:captcha:" + captchaId;
        String expected = redis.opsForValue().get(captchaKey);
        if (expected == null || !expected.equalsIgnoreCase(captchaCode)) {
            throw new BusinessException(ErrorCode.CAPTCHA_INVALID);
        }
        redis.delete(captchaKey);

        String ip = ClientIps.current();
        checkRate("account:sms:resend:" + phone, RESEND_INTERVAL, 1);
        checkRate("account:sms:phone-day:" + phone, Duration.ofDays(1), PHONE_DAILY_LIMIT);
        checkRate("account:sms:ip-hour:" + ip, Duration.ofHours(1), IP_HOURLY_LIMIT);
        checkRate("account:sms:ip-day:" + ip, Duration.ofDays(1), IP_DAILY_LIMIT);

        String key = smsKey(scene, phone);
        redis.opsForValue().set(key, DEV_SMS_CODE, SMS_TTL);
        redis.delete(wrongKey(scene, phone));
        return 300;
    }

    /** 校验短信验证码：输错 5 次即失效，成功后立即核销 */
    public void verifySmsCode(String scene, String phone, String code) {
        String key = smsKey(scene, phone);
        String expected = redis.opsForValue().get(key);
        if (expected == null) {
            throw new BusinessException(ErrorCode.SMS_CODE_INVALID);
        }
        if (!expected.equals(code)) {
            String wrong = wrongKey(scene, phone);
            Long count = redis.opsForValue().increment(wrong);
            if (count != null && count >= SMS_MAX_WRONG) {
                redis.delete(key);
                redis.delete(wrong);
            } else {
                redis.expire(wrong, SMS_TTL);
            }
            throw new BusinessException(ErrorCode.SMS_CODE_INVALID);
        }
        redis.delete(key);
        redis.delete(wrongKey(scene, phone));
    }

    private void checkRate(String key, Duration window, int limit) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, window);
        }
        if (count != null && count > limit) {
            throw new BusinessException(ErrorCode.SMS_RATE_LIMITED);
        }
    }

    private static String smsKey(String scene, String phone) {
        return "account:sms:" + scene + ":" + phone;
    }

    private static String wrongKey(String scene, String phone) {
        return "account:sms:wrong:" + scene + ":" + phone;
    }
}
