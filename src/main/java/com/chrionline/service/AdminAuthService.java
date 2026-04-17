package com.chrionline.service;

import com.chrionline.dao.AdminDAO;
import com.chrionline.model.Admin;
import com.chrionline.security.RSAUtil;
import java.security.PublicKey;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AdminAuthService {

    private final AdminDAO adminDAO;
    
    // Map of username to current pending Challenge. 
    // En production, il faudrait un TTL pour nettoyer les defis non resolus.
    private final Map<String, String> pendingChallenges = new ConcurrentHashMap<>();

    public AdminAuthService(AdminDAO adminDAO) {
        this.adminDAO = adminDAO;
    }

    public String generateChallenge(String username) {
        // Verifie si l'admin existe et est actif
        Admin admin = adminDAO.findByUsername(username)
            .filter(Admin::isActive)
            .orElseThrow(() -> new IllegalArgumentException("Admin non existant ou desactive"));

        String challenge = UUID.randomUUID().toString();
        pendingChallenges.put(username, challenge);
        return challenge;
    }

    public Admin verifyChallenge(String username, String signatureBase64) {
        String expectedChallenge = pendingChallenges.remove(username);
        if (expectedChallenge == null) {
            throw new IllegalArgumentException("Aucun defi en attente ou defi expire");
        }

        Admin admin = adminDAO.findByUsername(username)
            .filter(Admin::isActive)
            .orElseThrow(() -> new IllegalArgumentException("Admin non existant ou desactive"));

        try {
            PublicKey pubKey = RSAUtil.decodePublicKey(admin.getPublicKey());
            boolean isValid = RSAUtil.verify(expectedChallenge, signatureBase64, pubKey);
            if (!isValid) {
                throw new IllegalArgumentException("Signature RSA invalide");
            }
            return admin;
        } catch (Exception e) {
            throw new IllegalArgumentException("Erreur lors de la verification RSA: " + e.getMessage());
        }
    }
}
