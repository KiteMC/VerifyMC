package team.kitemc.verifymc.mail;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.function.BiFunction;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private void init() {
        debugLog("Initializing MailService");
        FileConfiguration config = plugin.getConfig();
        String host = config.getString("smtp.host", "smtp.qq.com");
        int port = config.getInt("smtp.port", 587);
        String username = config.getString("smtp.username");
        String password = config.getString("smtp.password");
        from = config.getString("smtp.from", username);
        boolean enableSsl = config.getBoolean("smtp.enable_ssl", true);
        
        debugLog("SMTP Configuration: host=" + host + ", port=" + port + ", username=" + username + ", enableSsl=" + enableSsl);

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

    public boolean sendCode(String to, String subject, String code) {
        debugLog("sendCode called: to=" + to + ", subject=" + subject + ", code=" + code);
        try {
            String lang = plugin.getConfig().getString("language", "zh");
            debugLog("Using language: " + lang);
            
            // 优先从 plugins/VerifyMC/email 目录加载模板
            File emailDir = new File(plugin.getDataFolder(), "email");
            File templateFile = new File(emailDir, "verify_code_" + lang + ".html");
            String content;
            if (templateFile.exists()) {
                debugLog("Using custom template: " + templateFile.getAbsolutePath());
                content = new String(Files.readAllBytes(templateFile.toPath()), StandardCharsets.UTF_8);
                content = content.replace("{code}", code);
            } else {
                debugLog("Using default template");
                content = "<html><body><h2>Verify Code</h2><p>Your code is: <strong>" + code + "</strong></p></body></html>";
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
} 