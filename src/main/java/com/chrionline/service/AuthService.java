package com.chrionline.service;

import com.chrionline.dao.UserDAO;
import com.chrionline.model.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Business logic for user authentication and email verification.
 */
public class AuthService {

    public static final String EMAIL_NOT_VERIFIED_MESSAGE =
            "Email non verifie. Entrez le code recu par email ou demandez un nouvel envoi.";

    private static final Logger LOG = LogManager.getLogger(AuthService.class);
    private static final String EMAIL_REGEX = "^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$";
    private static final int USERNAME_MIN = 3;
    private static final int USERNAME_MAX = 50;
    private static final String INVALID_CREDENTIALS = "Invalid credentials";
    private static final int VERIFICATION_CODE_LENGTH = 6;
    private static final int VERIFICATION_TTL_MINUTES = 15;
    private static final int RESEND_COOLDOWN_SECONDS = 60;

    private final UserDAO userDAO;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserDAO userDAO) {
        this(userDAO, new EmailService());
    }

    public AuthService(UserDAO userDAO, EmailService emailService) {
        this.userDAO = userDAO;
        this.emailService = emailService;
    }

    public enum LoginStatus {
        SUCCESS,
        LOGIN_IP_VERIFICATION_REQUIRED
    }

    public static final class LoginResult {
        private final LoginStatus status;
        private final User user;

        public LoginResult(LoginStatus status, User user) {
            this.status = status;
            this.user = user;
        }

        public LoginStatus getStatus() { return status; }
        public User getUser() { return user; }
    }

    public User register(String username, String email, String plainPassword, String clientIp) {
        validateRegistrationInput(username, email, plainPassword);
        ensureEmailServiceAvailable();

        User user = buildAndSave(username, email, plainPassword, false);
        user.setTrustedLoginIp(normalizeIp(clientIp));
        userDAO.update(user);
        try {
            issueAndSendVerificationCode(user, true);
            return user;
        } catch (RuntimeException e) {
            userDAO.delete(user.getUserId());
            throw e;
        }
    }



    public LoginResult login(String email, String plainPassword, String clientIp) {
        validateEmail(email);

        User user = userDAO.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException(INVALID_CREDENTIALS));

        if (user.isSuspended()) {
            throw new IllegalArgumentException("Compte suspendu. Contactez l'administrateur.");
        }

        if (!PasswordUtils.verify(plainPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException(INVALID_CREDENTIALS);
        }

        if (!user.isEmailVerified()) {
            throw new IllegalArgumentException(EMAIL_NOT_VERIFIED_MESSAGE);
        }

        String normalizedIp = normalizeIp(clientIp);

        // Une verification email est requise a chaque connexion valide
        // avant la creation effective d'une session.
        ensureEmailServiceAvailable();
        issueAndSendLoginIpVerificationCode(user, normalizedIp, false);
        return new LoginResult(LoginStatus.LOGIN_IP_VERIFICATION_REQUIRED, user);
    }

    public User verifyEmail(String email, String code) {
        validateEmail(email);
        validateVerificationCode(code);

        User user = userDAO.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Compte introuvable."));

        if (user.isEmailVerified()) {
            return user;
        }
        if (user.getEmailVerificationCode() == null || !user.getEmailVerificationCode().equals(code)) {
            throw new IllegalArgumentException("Code de verification invalide.");
        }
        LocalDateTime expiresAt = user.getEmailVerificationExpiresAt();
        if (expiresAt == null || expiresAt.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Code de verification expire. Demandez un nouvel envoi.");
        }

        userDAO.markEmailVerified(user.getUserId());
        user.setEmailVerified(true);
        user.setEmailVerificationCode(null);
        user.setEmailVerificationExpiresAt(null);
        user.setEmailVerificationSentAt(null);
        return user;
    }

    public void resendVerificationCode(String email) {
        validateEmail(email);
        ensureEmailServiceAvailable();

        User user = userDAO.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Compte introuvable."));

        if (user.isEmailVerified()) {
            throw new IllegalArgumentException("Cet email est deja verifie.");
        }

        LocalDateTime lastSentAt = user.getEmailVerificationSentAt();
        if (lastSentAt != null && lastSentAt.plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Veuillez patienter avant de demander un nouveau code.");
        }

        issueAndSendVerificationCode(user, false);
    }

    public void resendLoginIpVerificationCode(String email, String clientIp) {
        validateEmail(email);
        ensureEmailServiceAvailable();

        User user = userDAO.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Compte introuvable."));

        if (!user.isEmailVerified()) {
            throw new IllegalArgumentException(EMAIL_NOT_VERIFIED_MESSAGE);
        }

        String normalizedIp = normalizeIp(clientIp);
        issueAndSendLoginIpVerificationCode(user, normalizedIp, true);
    }

    public User verifyLoginIp(String email, String code, String clientIp) {
        validateEmail(email);
        validateVerificationCode(code);

        User user = userDAO.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Compte introuvable."));

        String normalizedIp = normalizeIp(clientIp);
        if (normalizedIp == null || normalizedIp.isBlank()) {
            throw new IllegalArgumentException("Adresse IP cliente indisponible.");
        }
        if (user.getLoginIpVerificationCode() == null || !user.getLoginIpVerificationCode().equals(code)) {
            throw new IllegalArgumentException("Code de verification invalide.");
        }
        LocalDateTime expiresAt = user.getLoginIpVerificationExpiresAt();
        if (expiresAt == null || expiresAt.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Code de verification expire. Demandez un nouvel envoi.");
        }
        if (user.getLoginIpVerificationPendingIp() == null
                || !normalizedIp.equals(user.getLoginIpVerificationPendingIp())) {
            throw new IllegalArgumentException("Le code recu ne correspond pas a cette adresse IP.");
        }

        userDAO.trustLoginIp(user.getUserId(), normalizedIp);
        user.setTrustedLoginIp(normalizedIp);
        user.setLoginIpVerificationCode(null);
        user.setLoginIpVerificationExpiresAt(null);
        user.setLoginIpVerificationSentAt(null);
        user.setLoginIpVerificationPendingIp(null);
        return user;
    }

    public void changePassword(int userId, String oldPlainPassword, String newPlainPassword) {
        User user = userDAO.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!PasswordUtils.verify(oldPlainPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        validatePasswordStrength(user.getUsername(), newPlainPassword);
        userDAO.updatePassword(userId, PasswordUtils.hash(newPlainPassword));
    }

    public User updateProfile(int userId, String newUsername, String newEmail) {
        User user = userDAO.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        validateUsername(newUsername);
        validateEmail(newEmail);

        if (!newUsername.equals(user.getUsername()) && userDAO.existsByUsername(newUsername)) {
            throw new IllegalArgumentException("Username already taken");
        }
        boolean emailChanged = !newEmail.equals(user.getEmail());
        if (emailChanged && userDAO.existsByEmail(newEmail)) {
            throw new IllegalArgumentException("Email already in use");
        }
        if (emailChanged) {
            ensureEmailServiceAvailable();
        }

        user.setUsername(newUsername);
        user.setEmail(newEmail);
        if (emailChanged) {
            user.setEmailVerified(false);
            userDAO.update(user);
            issueAndSendVerificationCode(user, false);
            throw new IllegalArgumentException("Email modifie. Un code de verification vient d'etre envoye a la nouvelle adresse.");
        }

        userDAO.update(user);
        return user;
    }

    public void forgotPassword(String email) {
        validateEmail(email);
        ensureEmailServiceAvailable();

        User user = userDAO.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Aucun compte trouve avec cet email."));

        if (user.isSuspended()) {
            throw new IllegalArgumentException("Compte suspendu. Contactez l'administrateur.");
        }

        String code = generatePasswordResetCode();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(VERIFICATION_TTL_MINUTES);

        userDAO.updatePasswordResetChallenge(user.getUserId(), code, expiresAt);
        user.setPasswordResetToken(code);
        user.setPasswordResetExpiresAt(expiresAt);

        try {
            emailService.sendPasswordResetEmail(user, code, expiresAt);
        } catch (RuntimeException e) {
            LOG.warn("[AUTH] Password reset email send failed for userId={}: {}", user.getUserId(), e.getMessage());
            throw e;
        }
    }

    public void resetPassword(String code, String newPlainPassword) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code de reinitialisation est requis.");
        }
        validateVerificationCode(code);

        User user = userDAO.findByPasswordResetToken(code)
                .orElseThrow(() -> new IllegalArgumentException("Code de reinitialisation invalide."));
        validatePasswordStrength(user.getUsername(), newPlainPassword);

        LocalDateTime expiresAt = user.getPasswordResetExpiresAt();
        if (expiresAt == null || expiresAt.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Code de reinitialisation expire.");
        }

        userDAO.updatePassword(user.getUserId(), PasswordUtils.hash(newPlainPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiresAt(null);
    }

    public void notifyFailedLoginAlert(String email, String clientIp, int failures) {
        if (email == null || email.isBlank() || failures < 3 || !emailService.isConfigured()) {
            if (!emailService.isConfigured()) {
                LOG.warn("[AUTH] Failed-login alert skipped because SMTP is not configured.");
            }
            return;
        }

        userDAO.findByEmail(email).ifPresent(user -> {
            try {
                LOG.info("[AUTH] Sending failed-login alert email to userId={} email={} failures={} ip={}",
                        user.getUserId(), user.getEmail(), failures, clientIp);
                emailService.sendFailedLoginAlertEmail(user, clientIp, failures);
                LOG.info("[AUTH] Failed-login alert email sent to userId={}", user.getUserId());
            } catch (RuntimeException e) {
                LOG.warn("[AUTH] Failed-login alert email send failed for userId={}: {}", user.getUserId(), e.getMessage());
            }
        });
    }

    private void validateRegistrationInput(String username, String email, String plainPassword) {
        validateUsername(username);
        validateEmail(email);
        validatePasswordStrength(username, plainPassword);

        if (userDAO.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already in use");
        }
        if (userDAO.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already taken");
        }
    }

    private User buildAndSave(String username, String email, String plainPassword, boolean emailVerified) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(PasswordUtils.hash(plainPassword));
        user.setCreatedAt(LocalDateTime.now());
        user.setEmailVerified(emailVerified);
        return userDAO.save(user);
    }

    private void issueAndSendVerificationCode(User user, boolean initialSend) {
        String code = generateVerificationCode();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(VERIFICATION_TTL_MINUTES);

        userDAO.updateVerificationChallenge(user.getUserId(), code, expiresAt, now);
        user.setEmailVerified(false);
        user.setEmailVerificationCode(code);
        user.setEmailVerificationExpiresAt(expiresAt);
        user.setEmailVerificationSentAt(now);

        try {
            emailService.sendVerificationEmail(user, code, expiresAt);
        } catch (RuntimeException e) {
            if (!initialSend) {
                LOG.warn("[AUTH] Email verification send failed for userId={}: {}", user.getUserId(), e.getMessage());
            }
            throw e;
        }
    }

    private boolean requiresLoginIpVerification(User user, String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return false;
        }
        String trustedIp = user.getTrustedLoginIp();
        return trustedIp == null || trustedIp.isBlank() || !trustedIp.equals(clientIp);
    }

    private void issueAndSendLoginIpVerificationCode(User user, String clientIp, boolean explicitResend) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastSentAt = user.getLoginIpVerificationSentAt();
        String pendingIp = user.getLoginIpVerificationPendingIp();
        LocalDateTime currentExpiry = user.getLoginIpVerificationExpiresAt();

        boolean sameIpPending = clientIp != null && clientIp.equals(pendingIp);
        boolean currentCodeStillValid = currentExpiry != null && currentExpiry.isAfter(now)
                && user.getLoginIpVerificationCode() != null && !user.getLoginIpVerificationCode().isBlank();

        if (explicitResend && lastSentAt != null
                && lastSentAt.plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(now)) {
            throw new IllegalArgumentException("Veuillez patienter avant de demander un nouveau code.");
        }

        if (!explicitResend && sameIpPending && currentCodeStillValid) {
            return;
        }

        String code = generateVerificationCode();
        LocalDateTime expiresAt = now.plusMinutes(VERIFICATION_TTL_MINUTES);

        userDAO.updateLoginIpVerificationChallenge(user.getUserId(), code, expiresAt, now, clientIp);
        user.setLoginIpVerificationCode(code);
        user.setLoginIpVerificationExpiresAt(expiresAt);
        user.setLoginIpVerificationSentAt(now);
        user.setLoginIpVerificationPendingIp(clientIp);

        try {
            emailService.sendLoginIpVerificationEmail(user, code, expiresAt, clientIp);
        } catch (RuntimeException e) {
            LOG.warn("[AUTH] Login IP verification send failed for userId={}: {}", user.getUserId(), e.getMessage());
            throw e;
        }
    }

    private String generateVerificationCode() {
        int bound = (int) Math.pow(10, VERIFICATION_CODE_LENGTH);
        int value = secureRandom.nextInt(bound);
        return String.format("%0" + VERIFICATION_CODE_LENGTH + "d", value);
    }

    private String generatePasswordResetCode() {
        int bound = (int) Math.pow(10, VERIFICATION_CODE_LENGTH);
        int value = secureRandom.nextInt(bound);
        return String.format("%0" + VERIFICATION_CODE_LENGTH + "d", value);
    }

    private void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        if (username.length() < USERNAME_MIN || username.length() > USERNAME_MAX) {
            throw new IllegalArgumentException(
                    "Username must be between " + USERNAME_MIN + " and " + USERNAME_MAX + " characters");
        }
    }

    private void validateEmail(String email) {
        if (email == null || !email.matches(EMAIL_REGEX)) {
            throw new IllegalArgumentException("Invalid email format");
        }
    }

    private void validatePasswordStrength(String username, String plainPassword) {
        if (!PasswordUtils.isStrongEnough(plainPassword)) {
            throw new IllegalArgumentException(
                    "Le mot de passe doit contenir au moins 8 caracteres, une majuscule, une minuscule, un chiffre et un caractere special.");
        }
        if (PasswordUtils.containsUsername(plainPassword, username)) {
            throw new IllegalArgumentException(
                    "Le mot de passe ne doit pas contenir le nom d'utilisateur.");
        }
    }

    private void validateVerificationCode(String code) {
        if (code == null || !code.matches("\\d{" + VERIFICATION_CODE_LENGTH + "}")) {
            throw new IllegalArgumentException("Code de verification invalide.");
        }
    }

    private void ensureEmailServiceAvailable() {
        if (!emailService.isConfigured()) {
            throw new IllegalArgumentException("Service email non configure. " + emailService.getConfigurationHelp());
        }
    }

    private String normalizeIp(String clientIp) {
        if (clientIp == null) {
            return null;
        }
        String normalized = clientIp.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
