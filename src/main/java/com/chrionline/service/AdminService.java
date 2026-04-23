package com.chrionline.service;

import com.chrionline.dao.OrderDAO;
import com.chrionline.dao.CategoryDAO;
import com.chrionline.dao.ProductDAO;
import com.chrionline.dao.UserDAO;
import com.chrionline.model.Category;
import com.chrionline.model.Order;
import com.chrionline.model.Product;
import com.chrionline.model.User;
import com.chrionline.protocol.Response;
import com.chrionline.security.RSAUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side admin operations (KAN-9).
 */
public class AdminService {

    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final UserDAO userDAO;
    private final EmailService emailService;

    public AdminService(UserDAO userDAO) {
        this(userDAO, new EmailService());
    }

    AdminService(UserDAO userDAO, EmailService emailService) {
        this.userDAO = userDAO;
        this.emailService = emailService;
    }

    // Products CRUD
    public Product createProduct(Product p) {
        validateProduct(p, false);
        return productDAO.save(p);
    }

    public void updateProduct(Product p) {
        validateProduct(p, true);
        productDAO.update(p);
    }

    public boolean toggleProductAvailability(int productId) {
        Product existing = productDAO.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Produit introuvable."));

        boolean nextAvailability = !existing.isAvailable();
        if (nextAvailability && existing.getStock() <= 0) {
            throw new IllegalArgumentException("Impossible d'activer un produit avec un stock nul.");
        }

        productDAO.updateAvailability(productId, nextAvailability);
        return nextAvailability;
    }

    // Orders
    public List<Order> listOrders() {
        return orderDAO.findAll();
    }

    // Users
    public List<Map<String, Object>> listUsers() {
        return userDAO.findAll().stream()
                .map(this::toUserSummary)
                .toList();
    }

    public void setUserSuspended(int userId, boolean suspended) {
        User user = userDAO.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
        if (user.getRole() == User.Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("Le compte SUPER_ADMIN unique ne peut pas etre suspendu.");
        }
        userDAO.setSuspended(userId, suspended);
    }

    public Map<String, Object> changeUserRole(int actorUserId, int targetUserId, User.Role targetRole, String publicKey) {
        if (actorUserId <= 0 || targetUserId <= 0) {
            throw new IllegalArgumentException("Utilisateur invalide.");
        }

        User actor = userDAO.findById(actorUserId)
                .orElseThrow(() -> new IllegalArgumentException("Compte acteur introuvable."));

        User target = userDAO.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur cible introuvable."));

        if (targetRole == null) {
            throw new IllegalArgumentException("Role cible invalide.");
        }
        boolean selfActivation = actorUserId == targetUserId
                && actor.getRole() == User.Role.ADMIN_PENDING
                && targetRole == User.Role.ADMIN;
        if (!selfActivation && actor.getRole() != User.Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("Seul un super admin peut changer les roles.");
        }
        if (!selfActivation && target.getRole() == User.Role.SUPER_ADMIN && targetRole != User.Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("Le role SUPER_ADMIN unique ne peut pas etre modifie.");
        }
        if (!selfActivation && targetRole == User.Role.SUPER_ADMIN && target.getRole() != User.Role.SUPER_ADMIN) {
            if (userDAO.countByRole(User.Role.SUPER_ADMIN) >= 1) {
                throw new IllegalArgumentException("Un seul compte SUPER_ADMIN est autorise.");
            }
        }

        User.Role previousRole = target.getRole();
        String normalizedPublicKey = normalizePublicKey(target, targetRole, publicKey);
        userDAO.updateRoleAndPublicKey(targetUserId, targetRole, normalizedPublicKey);

        target.setRole(targetRole);
        target.setPublicKey(normalizedPublicKey);
        notifyAdminPendingActivation(actor, target, previousRole);
        return toUserSummary(target);
    }

    // Categories CRUD (KAN-18)
    public Response addCategory(String name, String description) {
        try {
            if (name == null || name.isBlank()) {
                return Response.error("Missing name");
            }
            Category c = new Category();
            c.setName(name.trim());
            c.setDescription(description != null ? description : null);
            Category saved = categoryDAO.save(c);
            return Response.ok(saved);
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            return Response.error(e.getMessage());
        }
    }

    public Response updateCategory(int id, String name, String description) {
        try {
            if (id <= 0) {
                return Response.error("Missing id");
            }
            if (name == null || name.isBlank()) {
                return Response.error("Missing name");
            }
            Category c = new Category();
            c.setCategoryId(id);
            c.setName(name.trim());
            c.setDescription(description != null ? description : null);
            categoryDAO.update(c);
            return Response.ok("UPDATED", null);
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            return Response.error(e.getMessage());
        }
    }

    public Response deleteCategory(int id) {
        try {
            if (id <= 0) {
                return Response.error("Missing id");
            }
            // Reject deletion if any product is linked to this category
            List<Product> linked = productDAO.findByCategoryId(id);
            if (linked != null && !linked.isEmpty()) {
                return Response.error("Cannot delete category with linked products");
            }
            categoryDAO.delete(id);
            return Response.ok("DELETED", null);
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            return Response.error(e.getMessage());
        }
    }

    // ── Validation helpers (KAN-37) ──────────────────────────────────────────

    private void validateProduct(Product p, boolean requireId) {
        if (p == null) {
            throw new IllegalArgumentException("Produit invalide.");
        }
        if (requireId && p.getProductId() <= 0) {
            throw new IllegalArgumentException("product_id invalide.");
        }
        if (p.getCategoryId() <= 0) {
            throw new IllegalArgumentException("category_id invalide.");
        }
        if (p.getName() == null || p.getName().isBlank()) {
            throw new IllegalArgumentException("Le nom du produit est requis.");
        }
        if (p.getPrice() <= 0) {
            throw new IllegalArgumentException("Le prix doit être > 0.");
        }
        if (p.getStock() < 0) {
            throw new IllegalArgumentException("Le stock doit être >= 0.");
        }
        if (!requireId) {
            p.setAvailable(p.getStock() > 0);
            return;
        }

        Product existing = productDAO.findById(p.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Produit introuvable."));
        p.setAvailable(existing.isAvailable() && p.getStock() > 0);
    }

    private Map<String, Object> toUserSummary(User user) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", user.getUserId());
        payload.put("username", user.getUsername());
        payload.put("email", user.getEmail());
        payload.put("role", user.getRole().name());
        payload.put("suspended", user.isSuspended());
        payload.put("hasPublicKey", user.getPublicKey() != null && !user.getPublicKey().isBlank());
        return payload;
    }

    private String normalizePublicKey(User targetUser, User.Role role, String publicKey) {
        if (role == User.Role.CLIENT || role == User.Role.ADMIN_PENDING) {
            return null;
        }

        String source = publicKey;
        if ((source == null || source.isBlank()) && targetUser != null && targetUser.getPublicKey() != null && !targetUser.getPublicKey().isBlank()) {
            source = targetUser.getPublicKey();
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Une cle publique RSA est requise pour les roles admin.");
        }

        String normalized = source
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        try {
            RSAUtil.decodePublicKey(normalized);
            return normalized;
        } catch (Exception e) {
            throw new IllegalArgumentException("Cle publique RSA invalide.");
        }
    }

    private void notifyAdminPendingActivation(User actor, User target, User.Role previousRole) {
        if (actor == null || target == null) {
            return;
        }
        if (actor.getRole() != User.Role.SUPER_ADMIN) {
            return;
        }
        if (target.getRole() != User.Role.ADMIN_PENDING || previousRole == User.Role.ADMIN_PENDING) {
            return;
        }
        if (!emailService.isConfigured()) {
            return;
        }

        try {
            emailService.sendAdminPendingActivationEmail(target);
        } catch (RuntimeException ignored) {
            // Best-effort email: role change must still succeed even if SMTP fails.
        }
    }
}

