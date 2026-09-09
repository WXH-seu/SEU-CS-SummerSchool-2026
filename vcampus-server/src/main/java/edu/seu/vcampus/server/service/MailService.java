package edu.seu.vcampus.server.service;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Sends the password-reset verification code by SMTP. Credentials are loaded
 * from an external {@code mail.properties} file (git-ignored) so they never
 * reach version control.
 */
public final class MailService {
    private final Session session;
    private final String from;

    public MailService(Properties props) {
        Properties p = new Properties();
        String host = props.getProperty("mail.smtp.host", "smtp.qq.com");
        String port = props.getProperty("mail.smtp.port", "465");
        p.put("mail.smtp.host", host);
        p.put("mail.smtp.port", port);
        p.put("mail.smtp.auth", "true");
        p.put("mail.smtp.ssl.enable", "true");
        p.put("mail.smtp.ssl.trust", host);
        p.put("mail.smtp.ssl.protocols", props.getProperty("mail.smtp.ssl.protocols", "TLSv1.2"));
        final String user = props.getProperty("mail.user");
        final String password = props.getProperty("mail.password");
        this.from = props.getProperty("mail.from", user);
        this.session = Session.getInstance(p, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, password);
            }
        });
    }

    /** Loads the git-ignored mail configuration; returns {@code null} when absent. */
    public static MailService tryLoadDefault() {
        String path = System.getProperty("vcampus.mail.config",
                "vcampus-server/mail.properties");
        try {
            return loadFromFile(path);
        } catch (IOException e) {
            return null;
        }
    }

    public static MailService loadFromFile(String path) throws IOException {
        Properties props = new Properties();
        try (InputStream input = new FileInputStream(path)) {
            props.load(input);
        }
        return new MailService(props);
    }

    /** Sends the verification code as an HTML mail. */
    public void sendVerificationCode(String to, String code) throws MessagingException {
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject("东南大学虚拟校园 - 密码重置验证码");
        message.setContent(
                "您的密码重置验证码为：<b>" + code + "</b>，5 分钟内有效。"
                        + "若非本人操作，请忽略本邮件。",
                "text/html;charset=UTF-8");
        Transport.send(message);
    }
}
