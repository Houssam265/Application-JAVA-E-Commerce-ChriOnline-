package com.chrionline.service;

import com.chrionline.dao.UserDAO;
import com.chrionline.model.User;
import com.chrionline.security.RSAUtil;

import java.security.PublicKey;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AdminAuthService {

    private final UserDAO userDAO;
    private final Map<String, String> pendingChallenges = new ConcurrentHashMap<>();

    public AdminAuthService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public String generateChallenge(String username) {
        User user = userDAO.findByUsername(username)
                .filter(this::canUsePrivilegedLogin)
                .orElseThrow(() -> new IllegalArgumentException("Compte admin introuvable, inactif ou sans cle publique."));

        String challenge = UUID.randomUUID().toString();
        pendingChallenges.put(user.getUsername(), challenge);
        return challenge;
    }

    public User verifyChallenge(String username, String signatureBase64) {
        String expectedChallenge = pendingChallenges.remove(username);
        if (expectedChallenge == null) {
            throw new IllegalArgumentException("Aucun defi en attente ou defi expire");
        }

        User user = userDAO.findByUsername(username)
                .filter(this::canUsePrivilegedLogin)
                .orElseThrow(() -> new IllegalArgumentException("Compte admin introuvable, inactif ou sans cle publique."));

        try {
            PublicKey pubKey = RSAUtil.decodePublicKey(user.getPublicKey());
            boolean isValid = RSAUtil.verify(expectedChallenge, signatureBase64, pubKey);
            if (!isValid) {
                throw new IllegalArgumentException("Signature RSA invalide");
            }
            return user;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Erreur lors de la verification RSA: " + e.getMessage(), e);
        }
    }

    private boolean canUsePrivilegedLogin(User user) {
        return user != null
                && user.getRole().isPrivileged()
                && !user.isSuspended()
                && user.isEmailVerified()
                && user.getPublicKey() != null
                && !user.getPublicKey().isBlank();
    }
}
