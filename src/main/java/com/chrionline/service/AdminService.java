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

import java.util.List;

/**
 * Server-side admin operations (KAN-9).
 */
public class AdminService {

    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final UserDAO userDAO;

    public AdminService(UserDAO userDAO) {
        this.userDAO = userDAO;
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
    public List<User> listUsers() {
        return userDAO.findAll();
    }

    public void setUserSuspended(int userId, boolean suspended) {
        userDAO.setSuspended(userId, suspended);
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
}

