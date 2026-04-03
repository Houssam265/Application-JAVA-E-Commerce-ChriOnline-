package com.chrionline.service;

import com.chrionline.dao.UserDAO;
import com.chrionline.model.User;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Business logic for user authentication and email verification.
 */
public class AuthService {

    public static final String EMAIL_NOT_VERIFIED_MESSAGE =
            "Email non verifie. Entrez le code recu par email ou demandez un nouvel envoi.";

    private static final Logger LOG = Logger.getLogger(AuthService.class.getName());
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

    public User register(String username, String email, String plainPassword) {
        validateRegistrationInput(username, email, plainPassword);
        ensureEmailServiceAvailable();

        User user = buildAndSave(username, email, plainPassword, User.Role.CLIENT, false);
        try {
            issueAndSendVerificationCode(user, true);
            return user;
        } catch (RuntimeException e) {
            userDAO.delete(user.getUserId());
            throw e;
        }
    }

    public boolean isAdmin(User user) {
        return user.getRole() == User.Role.ADMIN;
    }

    public User createAdminUser(String username, String email, String plainPassword) {
        validateRegistrationInput(username, email, plainPassword);
        return buildAndSave(username, email, plainPassword, User.Role.ADMIN, true);
    }

    public void seedAdminIfNotExists() {
        if (!userDAO.existsByUsername("admin")) {
            createAdminUser("admin", "admin@chrionline.ma", "admin1234");
            LOG.info("[AUTH] Default admin account created");
        } else {
            LOG.info("[AUTH] Admin account already exists - skipping seed");
        }
    }

    public User login(String email, String plainPassword) {
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

        return user;
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

    public void changePassword(int userId, String oldPlainPassword, String newPlainPassword) {
        User user = userDAO.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!PasswordUtils.verify(oldPlainPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        validatePasswordStrength(newPlainPassword);
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

    private void validateRegistrationInput(String username, String email, String plainPassword) {
        validateUsername(username);
        validateEmail(email);
        validatePasswordStrength(plainPassword);

        if (userDAO.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already in use");
        }
        if (userDAO.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already taken");
        }
    }

    private User buildAndSave(String username, String email, String plainPassword, User.Role role, boolean emailVerified) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(PasswordUtils.hash(plainPassword));
        user.setRole(role);
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
                LOG.warning("[AUTH] Email verification send failed for userId=" + user.getUserId() + ": " + e.getMessage());
            }
            throw e;
        }
    }

    private String generateVerificationCode() {
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

    private void validatePasswordStrength(String plainPassword) {
        if (!PasswordUtils.isStrongEnough(plainPassword)) {
            throw new IllegalArgumentException(
                    "Password must be at least 8 characters and contain letters and digits");
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
}
