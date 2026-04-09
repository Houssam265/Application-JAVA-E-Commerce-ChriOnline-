package com.chrionline.service;

import com.chrionline.model.User;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

public class EmailService {

    private static final Logger LOG = LogManager.getLogger(EmailService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final String host = readConfig("CHRIONLINE_SMTP_HOST", "chrionline.smtp.host", "smtp.gmail.com");
    private final int port = Integer.parseInt(readConfig("CHRIONLINE_SMTP_PORT", "chrionline.smtp.port", "587"));
    private final String username = readConfig("CHRIONLINE_SMTP_USERNAME", "chrionline.smtp.username");
    private final String password = readConfig("CHRIONLINE_SMTP_PASSWORD", "chrionline.smtp.password");
    private final String from = readConfig("CHRIONLINE_SMTP_FROM", "chrionline.smtp.from", username);
    private final boolean auth = Boolean.parseBoolean(readConfig("CHRIONLINE_SMTP_AUTH", "chrionline.smtp.auth", "true"));
    private final boolean startTls = Boolean.parseBoolean(readConfig("CHRIONLINE_SMTP_STARTTLS", "chrionline.smtp.starttls", "true"));

    public void sendVerificationEmail(User user, String code, LocalDateTime expiresAt) {
        ensureConfigured();

        String subject = "Verification de votre adresse email ChriOnline";
        String html = buildHtmlBody(user, code, expiresAt);

        try {
            Session session = buildSession();
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(user.getEmail(), false));
            message.setSubject(subject, "UTF-8");
            message.setContent(html, "text/html; charset=UTF-8");
            Transport.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Impossible d'envoyer l'email de verification: " + e.getMessage(), e);
        }
    }

    public void sendLoginIpVerificationEmail(User user, String code, LocalDateTime expiresAt, String clientIp) {
        ensureConfigured();

        String subject = "Verification de connexion ChriOnline";
        String html = buildLoginIpHtmlBody(user, code, expiresAt, clientIp);

        try {
            Session session = buildSession();
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(user.getEmail(), false));
            message.setSubject(subject, "UTF-8");
            message.setContent(html, "text/html; charset=UTF-8");
            Transport.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Impossible d'envoyer l'email de verification de connexion: " + e.getMessage(), e);
        }
    }

    public void sendPasswordResetEmail(User user, String token, LocalDateTime expiresAt) {
        ensureConfigured();

        String subject = "Reinitialisation de votre mot de passe ChriOnline";
        String html = buildPasswordResetHtmlBody(user, token, expiresAt);

        try {
            Session session = buildSession();
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(user.getEmail(), false));
            message.setSubject(subject, "UTF-8");
            message.setContent(html, "text/html; charset=UTF-8");
            Transport.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Impossible d'envoyer l'email de reinitialisation: " + e.getMessage(), e);
        }
    }

    public boolean isConfigured() {
        return host != null && !host.isBlank()
                && from != null && !from.isBlank()
                && (!auth || (username != null && !username.isBlank() && password != null && !password.isBlank()));
    }

    public String getConfigurationHelp() {
        return "Configurer CHRIONLINE_SMTP_HOST, CHRIONLINE_SMTP_PORT, CHRIONLINE_SMTP_USERNAME, "
                + "CHRIONLINE_SMTP_PASSWORD et CHRIONLINE_SMTP_FROM.";
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            LOG.warn("[EMAIL] SMTP non configure. {}", getConfigurationHelp());
            throw new IllegalStateException("Service email non configure. " + getConfigurationHelp());
        }
    }

    private Session buildSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", String.valueOf(auth));
        props.put("mail.smtp.starttls.enable", String.valueOf(startTls));
        props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");

        if (auth) {
            return Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });
        }
        return Session.getInstance(props);
    }

    private String buildHtmlBody(User user, String code, LocalDateTime expiresAt) {
        return """
            <html>
              <body style="font-family: Arial, sans-serif; color: #0f172a;">
                <h2 style="margin-bottom: 8px;">Verification de votre email</h2>
                <p>Bonjour %s,</p>
                <p>Utilisez le code suivant pour activer votre compte ChriOnline :</p>
                <p style="font-size: 28px; font-weight: 700; letter-spacing: 6px;">%s</p>
                <p>Ce code expire le <strong>%s</strong>.</p>
                <p>Si vous n'etes pas a l'origine de cette demande, ignorez cet email.</p>
              </body>
            </html>
            """.formatted(escapeHtml(user.getUsername()), escapeHtml(code), escapeHtml(DATE_FORMAT.format(expiresAt)));
    }

    private String buildLoginIpHtmlBody(User user, String code, LocalDateTime expiresAt, String clientIp) {
        return """
            <html>
              <body style="font-family: Arial, sans-serif; color: #0f172a;">
                <h2 style="margin-bottom: 8px;">Verification de connexion</h2>
                <p>Bonjour %s,</p>
                <p>Une tentative de connexion a ete detectee depuis une nouvelle adresse IP :</p>
                <p><strong>%s</strong></p>
                <p>Entrez ce code dans l'application pour autoriser cette connexion :</p>
                <p style="font-size: 28px; font-weight: 700; letter-spacing: 6px;">%s</p>
                <p>Ce code expire le <strong>%s</strong>.</p>
                <p>Si cette tentative ne vient pas de vous, ignorez cet email et changez votre mot de passe.</p>
              </body>
            </html>
            """.formatted(
                escapeHtml(user.getUsername()),
                escapeHtml(clientIp == null ? "inconnue" : clientIp),
                escapeHtml(code),
                escapeHtml(DATE_FORMAT.format(expiresAt))
        );
    }

    private String buildPasswordResetHtmlBody(User user, String code, LocalDateTime expiresAt) {
        return """
            <html>
              <body style="font-family: Arial, sans-serif; color: #0f172a;">
                <h2 style="margin-bottom: 8px;">Reinitialisation de votre mot de passe</h2>
                <p>Bonjour %s,</p>
                <p>Vous avez demande la reinitialisation de votre mot de passe ChriOnline.</p>
                <p>Entrez ce code dans l'application pour definir un nouveau mot de passe :</p>
                <p style="font-size: 28px; font-weight: 700; letter-spacing: 6px;">%s</p>
                <p>Ce code expire le <strong>%s</strong>.</p>
                <p>Si vous n'etes pas a l'origine de cette demande, ignorez cet email.</p>
              </body>
            </html>
            """.formatted(
                escapeHtml(user.getUsername()),
                escapeHtml(code),
                escapeHtml(DATE_FORMAT.format(expiresAt))
        );
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String readConfig(String envKey, String propertyKey) {
        return readConfig(envKey, propertyKey, null);
    }

    private String readConfig(String envKey, String propertyKey, String defaultValue) {
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String envStyleProperty = System.getProperty(envKey);
        if (envStyleProperty != null && !envStyleProperty.isBlank()) {
            return envStyleProperty.trim();
        }
        String property = System.getProperty(propertyKey);
        if (property != null && !property.isBlank()) {
            return property.trim();
        }
        return defaultValue;
    }
}
