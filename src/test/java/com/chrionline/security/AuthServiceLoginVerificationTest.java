package com.chrionline.security;

import com.chrionline.dao.UserDAO;
import com.chrionline.model.User;
import com.chrionline.service.AuthService;
import com.chrionline.service.EmailService;
import com.chrionline.service.PasswordUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceLoginVerificationTest {

    @Test
    void login_succeedsWithoutVerificationWhenIpIsTrusted() {
        UserDAO userDAO = mock(UserDAO.class);
        EmailService emailService = mock(EmailService.class);

        User user = new User();
        user.setUserId(7);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash(PasswordUtils.hash("Strong!123"));
        user.setEmailVerified(true);
        user.setTrustedLoginIp("127.0.0.1");
        user.setCreatedAt(LocalDateTime.now());

        when(userDAO.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(emailService.isConfigured()).thenReturn(true);

        AuthService authService = new AuthService(userDAO, emailService);

        AuthService.LoginResult result = authService.login("alice@example.com", "Strong!123", "127.0.0.1");

        assertEquals(AuthService.LoginStatus.SUCCESS, result.getStatus());
        verify(userDAO, never()).updateLoginIpVerificationChallenge(eq(7), any(), any(), any(), anyString());
        verify(emailService, never()).sendLoginIpVerificationEmail(eq(user), any(), any(), anyString());
    }

    @Test
    void login_requiresVerificationWhenIpChanges() {
        UserDAO userDAO = mock(UserDAO.class);
        EmailService emailService = mock(EmailService.class);

        User user = new User();
        user.setUserId(7);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash(PasswordUtils.hash("Strong!123"));
        user.setEmailVerified(true);
        user.setTrustedLoginIp("127.0.0.1");
        user.setCreatedAt(LocalDateTime.now());

        when(userDAO.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(emailService.isConfigured()).thenReturn(true);

        AuthService authService = new AuthService(userDAO, emailService);

        AuthService.LoginResult result = authService.login("alice@example.com", "Strong!123", "127.0.0.2");

        assertEquals(AuthService.LoginStatus.LOGIN_IP_VERIFICATION_REQUIRED, result.getStatus());
        verify(userDAO).updateLoginIpVerificationChallenge(eq(7), any(), any(), any(), eq("127.0.0.2"));
        verify(emailService).sendLoginIpVerificationEmail(eq(user), any(), any(), eq("127.0.0.2"));
    }
}
