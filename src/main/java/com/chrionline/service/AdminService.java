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
        return productDAO.save(p);
    }

    public void updateProduct(Product p) {
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
}

