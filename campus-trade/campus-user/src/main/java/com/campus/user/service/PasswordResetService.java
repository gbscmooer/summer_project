package com.campus.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.common.exception.BizException;
import com.campus.common.result.ResultCode;
import com.campus.user.entity.PasswordResetToken;
import com.campus.user.entity.User;
import com.campus.user.mail.ResendMailService;
import com.campus.user.mapper.PasswordResetTokenMapper;
import com.campus.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int TOKEN_TTL_MINUTES = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final PasswordResetTokenMapper tokenMapper;
    private final ResendMailService mailService;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${campus.mail.public-base-url:http://127.0.0.1:8080}")
    private String publicBaseUrl;

    /**
     * 发起找回：即使用户不存在也返回成功文案，防枚举。
     */
    @Transactional(rollbackFor = Exception.class)
    public void requestReset(String username) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username.trim()));
        if (user == null) {
            log.info("密码重置：用户名不存在，静默成功");
            return;
        }
        if (!StringUtils.hasText(user.getEmail())) {
            // 账号存在但无邮箱：仍静默，避免泄露；日志便于排障
            log.warn("密码重置：用户未绑定邮箱 userId={}", user.getId());
            return;
        }

        // 作废旧未使用令牌
        tokenMapper.update(null, new LambdaUpdateWrapper<PasswordResetToken>()
                .eq(PasswordResetToken::getUserId, user.getId())
                .isNull(PasswordResetToken::getUsedAt)
                .set(PasswordResetToken::getUsedAt, LocalDateTime.now()));

        String rawToken = randomToken();
        PasswordResetToken row = new PasswordResetToken();
        row.setUserId(user.getId());
        row.setTokenHash(sha256(rawToken));
        row.setExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_TTL_MINUTES));
        tokenMapper.insert(row);

        String link = trimSlash(publicBaseUrl) + "/reset-password?token=" + rawToken;
        String safeName = HtmlUtils.htmlEscape(user.getUsername());
        String safeLink = HtmlUtils.htmlEscape(link);
        String html = buildResetEmailHtml(safeName, safeLink, TOKEN_TTL_MINUTES);

        mailService.sendHtml(user.getEmail().trim(), "重置你的校园集市密码", html);
    }

    /** 邮件客户端友好的 table 布局：标题、原因、按钮、页脚。 */
    private String buildResetEmailHtml(String safeUsername, String safeResetUrl, int ttlMinutes) {
        String site = trimSlash(publicBaseUrl);
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>重置密码</title>
                </head>
                <body style="margin:0;padding:0;background:#f4f4f5;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f4f4f5;padding:32px 16px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:560px;background:#ffffff;border-radius:16px;overflow:hidden;border:1px solid #e8e8ea;">
                          <tr>
                            <td style="padding:28px 32px 8px;font-size:22px;font-weight:700;color:#111111;letter-spacing:-0.02em;">
                              校园集市
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:8px 32px 0;font-size:18px;font-weight:600;color:#111111;">
                              重置你的密码
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:12px 32px 0;font-size:15px;line-height:1.6;color:#444444;">
                              你好，%s：
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:8px 32px 0;font-size:15px;line-height:1.7;color:#555555;">
                              <strong style="color:#111111;">原因：</strong>我们收到了与你的账号相关的密码重置请求。
                              若确认是你本人操作，请在 <strong>%d 分钟</strong>内点击下方按钮设置新密码。
                            </td>
                          </tr>
                          <tr>
                            <td align="center" style="padding:28px 32px 8px;">
                              <a href="%s"
                                 style="display:inline-block;background:#111111;color:#ffffff;text-decoration:none;font-size:15px;font-weight:600;padding:12px 28px;border-radius:999px;">
                                重置密码
                              </a>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:12px 32px 0;font-size:13px;line-height:1.6;color:#888888;">
                              如果按钮无法点击，请复制以下链接到浏览器打开：<br />
                              <a href="%s" style="color:#555555;word-break:break-all;">%s</a>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:20px 32px 28px;font-size:13px;line-height:1.6;color:#888888;">
                              如果不是你本人操作，请忽略本邮件，你的密码不会被更改。
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:0 16px 16px;">
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f3f3f4;border-radius:14px;">
                                <tr>
                                  <td style="padding:20px 22px;">
                                    <div style="font-size:15px;font-weight:700;color:#111111;margin-bottom:8px;">校园集市</div>
                                    <div style="font-size:13px;line-height:1.5;color:#666666;">
                                      Campus Market<br />
                                      summer.huangzixuan.asia
                                    </div>
                                    <div style="margin-top:10px;font-size:13px;color:#666666;">
                                      <a href="%s/settings" style="color:#666666;text-decoration:none;">隐私</a>
                                      &nbsp;·&nbsp;
                                      <a href="%s/settings" style="color:#666666;text-decoration:none;">条款</a>
                                    </div>
                                    <div style="margin-top:12px;font-size:12px;color:#999999;">
                                      校园集市 © 2026
                                    </div>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                safeUsername,
                ttlMinutes,
                safeResetUrl,
                safeResetUrl,
                safeResetUrl,
                site,
                site
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(String rawToken, String newPassword) {
        if (!StringUtils.hasText(rawToken) || !StringUtils.hasText(newPassword)) {
            throw new BizException(ResultCode.RESET_TOKEN_INVALID);
        }
        PasswordResetToken row = tokenMapper.selectOne(new LambdaQueryWrapper<PasswordResetToken>()
                .eq(PasswordResetToken::getTokenHash, sha256(rawToken.trim()))
                .last("LIMIT 1"));
        if (row == null || row.getUsedAt() != null
                || row.getExpiresAt() == null
                || row.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ResultCode.RESET_TOKEN_INVALID);
        }

        User user = userMapper.selectById(row.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.RESET_TOKEN_INVALID);
        }

        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getPassword, passwordEncoder.encode(newPassword)));

        tokenMapper.update(null, new LambdaUpdateWrapper<PasswordResetToken>()
                .eq(PasswordResetToken::getId, row.getId())
                .set(PasswordResetToken::getUsedAt, LocalDateTime.now()));
    }

    private static String randomToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    private static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String trimSlash(String base) {
        if (base == null) {
            return "";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
