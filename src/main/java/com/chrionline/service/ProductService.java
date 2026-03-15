package com.chrionline.service;

import com.chrionline.dao.CategoryDAO;
import com.chrionline.dao.ProductDAO;
import com.chrionline.model.Category;
import com.chrionline.model.Product;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Business logic for catalogue: products and categories.
 * Used by the server to handle GET_PRODUCTS, GET_PRODUCT, GET_CATEGORIES.
 */
public class ProductService {

    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();

    /**
     * Returns all products, optionally filtered by category.
     *
     * @param categoryId optional; if null, returns all products
     * @return list of products (never null)
     */
    public List<Product> getProducts(Integer categoryId) {
        if (categoryId != null) {
            return productDAO.findByCategoryId(categoryId);
        }
        return productDAO.findAll();
    }

    /**
     * Returns product details including category (name, description).
     * Used for GET_PRODUCT response: nom, prix, description, stock, catégorie.
     *
     * @param productId product primary key
     * @return map with keys "product" (Product) and "category" (Category), or empty if not found
     */
    public Optional<Map<String, Object>> getProductDetails(int productId) {
        Optional<Product> optProduct = productDAO.findById(productId);
        if (optProduct.isEmpty()) {
            return Optional.empty();
        }
        Product product = optProduct.get();
        Optional<Category> optCategory = categoryDAO.findById(product.getCategoryId());
        Category category = optCategory.orElse(null);

        Map<String, Object> data = new HashMap<>();
        data.put("product", product);
        data.put("category", category);
        return Optional.of(data);
    }

    /**
     * Returns all categories for the catalogue filter.
     */
    public List<Category> getCategories() {
        return categoryDAO.findAll();
    }
}
