package com.chrionline.service;

import com.chrionline.dao.UserDAO;
import com.chrionline.model.User;

import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Business logic for user authentication.
 * <p>
 * Pure service layer — no TCP, no sockets, no JSON, no JDBC.
 * All BCrypt operations are delegated to {@link PasswordUtils}.
 * All validation failures throw {@link IllegalArgumentException} with a
 * human-readable message so {@code ClientHandler} can relay them as a
 * {@code Response.error(message)}.
 */
public class AuthService {

    // ── Validation constants ─────────────────────────────────────────────────

    private static final String EMAIL_REGEX        = "^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$";
    private static final int    USERNAME_MIN        = 3;
    private static final int    USERNAME_MAX        = 50;
    private static final String INVALID_CREDENTIALS = "Invalid credentials";

    // ── Logger ───────────────────────────────────────────────────────────────

    private static final Logger LOG = Logger.getLogger(AuthService.class.getName());

    // ── Dependencies ─────────────────────────────────────────────────────────

    private final UserDAO userDAO;

    /**
     * Constructor injection — no static methods, no getInstance().
     *
     * @param userDAO the DAO used for all user persistence operations
     */
    public AuthService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Registers a new user with CLIENT role.
     *
     * @param username      3–50 characters, not blank
     * @param email         valid email format
     * @param plainPassword meets {@link PasswordUtils#isStrongEnough(String)}
     * @return the persisted {@link User} with generated userId and createdAt set
     * @throws IllegalArgumentException on any validation or uniqueness failure
     */
    public User register(String username, String email, String plainPassword) {
        validateRegistrationInput(username, email, plainPassword);
        return buildAndSave(username, email, plainPassword, User.Role.CLIENT);
    }

    // ── Role management ──────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the user has the ADMIN role.
     * <p>
     * Pure in-memory check — no DB call.
     *
     * @param user the user to check (must not be {@code null})
     * @return {@code true} if {@code user.getRole() == Role.ADMIN}
     */
    public boolean isAdmin(User user) {
        return user.getRole() == User.Role.ADMIN;
    }

    /**
     * Creates a user with {@link User.Role#ADMIN} role.
     * <p>
     * Applies identical validation and uniqueness checks as {@link #register}.
     * Intended for server-startup seeding only — not exposed as a TCP action.
     *
     * @param username      3–50 characters, not blank
     * @param email         valid email format
     * @param plainPassword meets {@link PasswordUtils#isStrongEnough(String)}
     * @return the persisted admin {@link User}
     * @throws IllegalArgumentException on any validation or uniqueness failure
     */
    public User createAdminUser(String username, String email, String plainPassword) {
        validateRegistrationInput(username, email, plainPassword);
        return buildAndSave(username, email, plainPassword, User.Role.ADMIN);
    }

    /**
     * Seeds the default admin account if it does not already exist.
     * <p>
     * Safe to call multiple times — idempotent by design.
     * Intended to be called once from {@code Server.java} at startup.
     */
    public void seedAdminIfNotExists() {
        if (!userDAO.existsByUsername("admin")) {
            createAdminUser("admin", "admin@chrionline.ma", "admin1234");
            LOG.info("[AUTH] Default admin account created");
        } else {
            LOG.info("[AUTH] Admin account already exists — skipping seed");
        }
    }

    /**
     * Authenticates a user by email and password.
     * <p>
     * Always throws the same message for wrong email or wrong password to
     * prevent user-enumeration attacks.
     *
     * @param email         the account email
     * @param plainPassword the candidate plain-text password
     * @return the authenticated {@link User}
     * @throws IllegalArgumentException with "Invalid credentials" on any failure
     */
    public User login(String email, String plainPassword) {
        // Advanced-level validation: reject malformed emails at the service layer
        // (even if the UI validates, a TCP client can bypass it).
        validateEmail(email);

        User user = userDAO.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException(INVALID_CREDENTIALS));

        if (user.isSuspended()) {
            throw new IllegalArgumentException("Compte suspendu. Contactez l'administrateur.");
        }

        if (!PasswordUtils.verify(plainPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException(INVALID_CREDENTIALS);
        }

        return user;
    }

    /**
     * Changes the password of an existing user after verifying the old one.
     *
     * @param userId          the user whose password to change
     * @param oldPlainPassword the current plain-text password for verification
     * @param newPlainPassword the new plain-text password (must pass strength check)
     * @throws IllegalArgumentException if user not found, old password wrong, or
     *                                  new password too weak
     */
    public void changePassword(int userId, String oldPlainPassword, String newPlainPassword) {

        User user = userDAO.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!PasswordUtils.verify(oldPlainPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        validatePasswordStrength(newPlainPassword);

        userDAO.updatePassword(userId, PasswordUtils.hash(newPlainPassword));
    }

    /**
     * Updates the username and/or email of an existing user.
     *
     * @param userId      the user to update
     * @param newUsername new username (3–50 chars)
     * @param newEmail    new email (valid format)
     * @return the updated {@link User}
     * @throws IllegalArgumentException if user not found or new values already taken
     */
    public User updateProfile(int userId, String newUsername, String newEmail) {

        User user = userDAO.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        validateUsername(newUsername);
        validateEmail(newEmail);

        // Uniqueness — only check if the value actually changed
        if (!newUsername.equals(user.getUsername()) && userDAO.existsByUsername(newUsername)) {
            throw new IllegalArgumentException("Username already taken");
        }
        if (!newEmail.equals(user.getEmail()) && userDAO.existsByEmail(newEmail)) {
            throw new IllegalArgumentException("Email already in use");
        }

        user.setUsername(newUsername);
        user.setEmail(newEmail);

        userDAO.update(user);
        return user;
    }

    // ── Private validation helpers ───────────────────────────────────────────

    /**
     * Shared validation + uniqueness checks for both {@link #register} and
     * {@link #createAdminUser} — single source of truth, zero duplication.
     */
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

    /**
     * Constructs a {@link User}, hashes the password, and persists it with the given role.
     * Called by both {@link #register} and {@link #createAdminUser}.
     */
    private User buildAndSave(String username, String email, String plainPassword, User.Role role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(PasswordUtils.hash(plainPassword));
        user.setRole(role);
        user.setCreatedAt(LocalDateTime.now());
        return userDAO.save(user);
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

    /**
     * Delegates strength check entirely to {@link PasswordUtils#isStrongEnough(String)}.
     * Used in both {@link #register}/{@link #createAdminUser} and {@link #changePassword}.
     */
    private void validatePasswordStrength(String plainPassword) {
        if (!PasswordUtils.isStrongEnough(plainPassword)) {
            throw new IllegalArgumentException(
                    "Password must be at least 8 characters and contain letters and digits");
        }
    }
}
