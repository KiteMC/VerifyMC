package team.kitemc.verifymc.mail;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.function.BiFunction;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.io.File;

public class MailService {
    private final Plugin plugin;
    private final BiFunction<String, String, String> getMessage;
    private Session session;
    private String from;
    private final boolean debug;

    private void debugLog(String msg) {
        if (debug) plugin.getLogger().info("[DEBUG] MailService: " + msg);
    }

    public MailService(Plugin plugin, BiFunction<String, String, String> getMessage) {
        this.plugin = plugin;
        this.getMessage = getMessage;
        this.debug = plugin.getConfig().getBoolean("debug", false);
        init();
    }

    /**
     * Initialize mail service
     */
    private void init() {
        debugLog("Initializing MailService");
        FileConfiguration config = plugin.getConfig();
        String host = config.getString("smtp.host", "smtp.qq.com");
        int port = config.getInt("smtp.port", 587);
        String username = config.getString("smtp.username");
        String password = config.getString("smtp.password");
        from = config.getString("smtp.from", username);
        boolean enableSsl = config.getBoolean("smtp.enable_ssl", true);
        
        debugLog("SMTP Configuration: host=" + host + ", port=" + port + ", enableSsl=" + enableSsl);

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        if (enableSsl) {
            if (port == 465) {
                props.put("mail.smtp.ssl.enable", "true");
            } else {
                props.put("mail.smtp.starttls.enable", "true");
            }
        }
        session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                debugLog("Authenticating with SMTP server");
                return new PasswordAuthentication(username, password);
            }
        });
        debugLog("MailService initialized successfully");
    }

    /**
     * Send verification code email
     * @param to Recipient email address
     * @param subject Email subject
     * @param code Verification code
     * @param language User's interface language
     * @return true if email sent successfully
     */
    public boolean sendCode(String to, String subject, String code, String language) {
        debugLog("sendCode called: to=" + to + ", subject=" + subject + ", language=" + language);
        try {
            String lang = (language != null && !language.isEmpty()) ? language : plugin.getConfig().getString("language", "zh");
            debugLog("Using language: " + lang);
            
            File emailDir = new File(plugin.getDataFolder(), "email");
            File templateFile = new File(emailDir, "verify_code_" + lang + ".html");
            String content;
            if (templateFile.exists()) {
                debugLog("Using custom template: " + templateFile.getAbsolutePath());
                content = new String(Files.readAllBytes(templateFile.toPath()), StandardCharsets.UTF_8);
                content = content.replace("{code}", escapeHtml(code));
            } else {
                debugLog("Using default template");
                content = getDefaultVerifyCodeTemplate(lang, escapeHtml(code));
            }
            
            debugLog("Creating email message");
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setContent(content, "text/html; charset=utf-8");
            
            debugLog("Sending email");
            Transport.send(message);
            debugLog("Email sent successfully");
            return true;
        } catch (Exception e) {
            String lang = plugin.getConfig().getString("language", "zh");
            debugLog("Failed to send email: " + e.getMessage());
            plugin.getLogger().warning(getMessage.apply("email.failed", lang) + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Send verification code email (backward compatible)
     */
    public boolean sendCode(String to, String subject, String code) {
        return sendCode(to, subject, code, null);
    }

    /**
     * Send verification code email (alias for sendCode)
     */
    public boolean sendVerifyCode(String to, String subject, String code) {
        return sendCode(to, subject, code, null);
    }

    /**
     * Send verification code email (alias for sendCode with language)
     */
    public boolean sendVerifyCode(String to, String subject, String code, String language) {
        return sendCode(to, subject, code, language);
    }

    /**
     * Send verification code email with the configured subject.
     */
    public boolean sendVerifyCodeWithDefaultSubject(String to, String code, String language) {
        String subject = plugin.getConfig().getString("email_subject", "VerifyMC Verification Code");
        return sendCode(to, subject, code, language);
    }

    /**
     * Get default verify code template
     */
    private String getDefaultVerifyCodeTemplate(String lang, String code) {
        if ("zh".equals(lang)) {
            return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body style=\"font-family: Arial, sans-serif; padding: 20px;\">" +
                   "<div style=\"max-width: 600px; margin: 0 auto; background: #f9f9f9; padding: 30px; border-radius: 10px;\">" +
                   "<h2 style=\"color: #333;\">验证码</h2>" +
                   "<p style=\"color: #666;\">您的验证码是：</p>" +
                   "<div style=\"background: #fff; padding: 20px; border-radius: 8px; margin: 20px 0; text-align: center;\">" +
                   "<span style=\"font-size: 32px; font-weight: bold; color: #4CAF50; letter-spacing: 5px;\">" + code + "</span>" +
                   "</div>" +
                   "<p style=\"color: #999; font-size: 12px;\">此邮件由 VerifyMC 自动发送</p>" +
                   "</div></body></html>";
        } else {
            return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body style=\"font-family: Arial, sans-serif; padding: 20px;\">" +
                   "<div style=\"max-width: 600px; margin: 0 auto; background: #f9f9f9; padding: 30px; border-radius: 10px;\">" +
                   "<h2 style=\"color: #333;\">Verification Code</h2>" +
                   "<p style=\"color: #666;\">Your verification code is:</p>" +
                   "<div style=\"background: #fff; padding: 20px; border-radius: 8px; margin: 20px 0; text-align: center;\">" +
                   "<span style=\"font-size: 32px; font-weight: bold; color: #4CAF50; letter-spacing: 5px;\">" + code + "</span>" +
                   "</div>" +
                   "<p style=\"color: #999; font-size: 12px;\">This email was automatically sent by VerifyMC</p>" +
                   "</div></body></html>";
        }
    }
    
    /**
     * Check if user notification is enabled
     * @return true if user notification is enabled
     */
    public boolean isUserNotificationEnabled() {
        return plugin.getConfig().getBoolean("user_notification.enabled", true);
    }
    
    /**
     * Check if notification on approval is enabled
     * @return true if notification on approval is enabled
     */
    public boolean isNotifyOnApprove() {
        return plugin.getConfig().getBoolean("user_notification.on_approve", true);
    }
    
    /**
     * Check if notification on rejection is enabled
     * @return true if notification on rejection is enabled
     */
    public boolean isNotifyOnReject() {
        return plugin.getConfig().getBoolean("user_notification.on_reject", true);
    }
    
    /**
     * Send review result notification to user
     * @param email User's email address
     * @param username User's username
     * @param approved Whether the application was approved
     * @param reason Rejection reason (only used if rejected)
     * @param language User's interface language
     * @return true if email sent successfully
     */
    public boolean sendReviewResultNotification(String email, String username, boolean approved, String reason, String language) {
        if (!isUserNotificationEnabled()) {
            debugLog("User notification is disabled");
            return false;
        }
        
        if (approved && !isNotifyOnApprove()) {
            debugLog("Notification on approval is disabled");
            return false;
        }
        
        if (!approved && !isNotifyOnReject()) {
            debugLog("Notification on rejection is disabled");
            return false;
        }
        
        if (email == null || email.trim().isEmpty()) {
            debugLog("User email is empty");
            return false;
        }
        
        debugLog("sendReviewResultNotification called: email=" + email + ", username=" + username + ", approved=" + approved + ", language=" + language);
        
        try {
            String lang = (language != null && !language.isEmpty()) ? language : plugin.getConfig().getString("language", "zh");
            String templateName = approved ? "review_approved_" + lang + ".html" : "review_rejected_" + lang + ".html";
            String subject = approved ? 
                ("zh".equals(lang) ? "[VerifyMC] 您的白名单申请已通过" : "[VerifyMC] Your whitelist application has been approved") :
                ("zh".equals(lang) ? "[VerifyMC] 您的白名单申请被拒绝" : "[VerifyMC] Your whitelist application has been rejected");
            
            File emailDir = new File(plugin.getDataFolder(), "email");
            File templateFile = new File(emailDir, templateName);
            String content;
            
            if (templateFile.exists()) {
                debugLog("Using custom template: " + templateFile.getAbsolutePath());
                content = new String(Files.readAllBytes(templateFile.toPath()), StandardCharsets.UTF_8);
            } else {
                debugLog("Using default template");
                content = getDefaultReviewResultTemplate(lang, approved);
            }
            
            String serverName = plugin.getConfig().getString("web_server_prefix", "[ Server ]");
            content = content.replace("{username}", escapeHtml(username))
                             .replace("{server_name}", escapeHtml(serverName))
                             .replace("{reason}", escapeHtml(reason != null ? reason : ""));
            
            debugLog("Creating review result notification email");
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
            message.setSubject(subject);
            message.setContent(content, "text/html; charset=utf-8");
            
            debugLog("Sending review result notification to: " + email);
            Transport.send(message);
            debugLog("Review result notification sent successfully");
            return true;
        } catch (Exception e) {
            debugLog("Failed to send review result notification: " + e.getMessage());
            plugin.getLogger().warning("Failed to send review result notification: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Send review result notification (alias without language parameter)
     */
    public boolean sendReviewResult(String email, String username, boolean approved, String reason) {
        return sendReviewResultNotification(email, username, approved, reason, null);
    }
    
    /**
     * Send review result notification (alias for sendReviewResultNotification)
     */
    public boolean sendReviewResult(String email, String username, boolean approved, String reason, String language) {
        return sendReviewResultNotification(email, username, approved, reason, language);
    }
    
    /**
     * Get default review result template
     * @param lang Language code
     * @param approved Whether approved
     * @return Default HTML template
     */
    private String getDefaultReviewResultTemplate(String lang, boolean approved) {
        if ("zh".equals(lang)) {
            if (approved) {
                return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body style=\"font-family: Arial, sans-serif; padding: 20px;\">" +
                       "<div style=\"max-width: 600px; margin: 0 auto; background: #f9f9f9; padding: 30px; border-radius: 10px;\">" +
                       "<h2 style=\"color: #4CAF50;\">🎉 白名单申请已通过</h2>" +
                       "<p style=\"color: #666;\">恭喜！您在 {server_name} 的白名单申请已通过审核。</p>" +
                       "<div style=\"background: #fff; padding: 20px; border-radius: 8px; margin: 20px 0;\">" +
                       "<p><strong>用户名:</strong> {username}</p>" +
                       "<p><strong>状态:</strong> <span style=\"color: #4CAF50;\">已通过</span></p>" +
                       "</div>" +
                       "<p style=\"color: #666;\">您现在可以使用该用户名登录服务器了。</p>" +
                       "<p style=\"color: #999; font-size: 12px;\">此邮件由 VerifyMC 自动发送</p>" +
                       "</div></body></html>";
            } else {
                return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body style=\"font-family: Arial, sans-serif; padding: 20px;\">" +
                       "<div style=\"max-width: 600px; margin: 0 auto; background: #f9f9f9; padding: 30px; border-radius: 10px;\">" +
                       "<h2 style=\"color: #f44336;\">白名单申请被拒绝</h2>" +
                       "<p style=\"color: #666;\">很抱歉，您在 {server_name} 的白名单申请未能通过审核。</p>" +
                       "<div style=\"background: #fff; padding: 20px; border-radius: 8px; margin: 20px 0;\">" +
                       "<p><strong>用户名:</strong> {username}</p>" +
                       "<p><strong>状态:</strong> <span style=\"color: #f44336;\">已拒绝</span></p>" +
                       "<p><strong>原因:</strong> {reason}</p>" +
                       "</div>" +
                       "<p style=\"color: #666;\">如有疑问，请联系服务器管理员。</p>" +
                       "<p style=\"color: #999; font-size: 12px;\">此邮件由 VerifyMC 自动发送</p>" +
                       "</div></body></html>";
            }
        } else {
            if (approved) {
                return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body style=\"font-family: Arial, sans-serif; padding: 20px;\">" +
                       "<div style=\"max-width: 600px; margin: 0 auto; background: #f9f9f9; padding: 30px; border-radius: 10px;\">" +
                       "<h2 style=\"color: #4CAF50;\">🎉 Whitelist Application Approved</h2>" +
                       "<p style=\"color: #666;\">Congratulations! Your whitelist application for {server_name} has been approved.</p>" +
                       "<div style=\"background: #fff; padding: 20px; border-radius: 8px; margin: 20px 0;\">" +
                       "<p><strong>Username:</strong> {username}</p>" +
                       "<p><strong>Status:</strong> <span style=\"color: #4CAF50;\">Approved</span></p>" +
                       "</div>" +
                       "<p style=\"color: #666;\">You can now join the server using this username.</p>" +
                       "<p style=\"color: #999; font-size: 12px;\">This email was automatically sent by VerifyMC</p>" +
                       "</div></body></html>";
            } else {
                return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body style=\"font-family: Arial, sans-serif; padding: 20px;\">" +
                       "<div style=\"max-width: 600px; margin: 0 auto; background: #f9f9f9; padding: 30px; border-radius: 10px;\">" +
                       "<h2 style=\"color: #f44336;\">Whitelist Application Rejected</h2>" +
                       "<p style=\"color: #666;\">We're sorry, but your whitelist application for {server_name} has been rejected.</p>" +
                       "<div style=\"background: #fff; padding: 20px; border-radius: 8px; margin: 20px 0;\">" +
                       "<p><strong>Username:</strong> {username}</p>" +
                       "<p><strong>Status:</strong> <span style=\"color: #f44336;\">Rejected</span></p>" +
                       "<p><strong>Reason:</strong> {reason}</p>" +
                       "</div>" +
                       "<p style=\"color: #666;\">If you have any questions, please contact the server administrator.</p>" +
                       "<p style=\"color: #999; font-size: 12px;\">This email was automatically sent by VerifyMC</p>" +
                       "</div></body></html>";
            }
        }
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
