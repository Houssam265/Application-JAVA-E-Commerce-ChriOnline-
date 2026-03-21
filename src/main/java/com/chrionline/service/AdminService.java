package com.chrionline.service;

import com.chrionline.dao.OrderDAO;
import com.chrionline.dao.ProductDAO;
import com.chrionline.dao.UserDAO;
import com.chrionline.model.Order;
import com.chrionline.model.Product;
import com.chrionline.model.User;

import java.util.List;

/**
 * Server-side admin operations (KAN-9).
 */
public class AdminService {

    private final ProductDAO productDAO = new ProductDAO();
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

    public void deleteProduct(int productId) {
        productDAO.delete(productId);
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
        p.setAvailable(p.getStock() > 0);
    }
}

