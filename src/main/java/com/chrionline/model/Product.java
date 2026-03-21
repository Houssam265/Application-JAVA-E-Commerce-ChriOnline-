package com.chrionline.model;

/**
 * Maps to the {@code products} table.
 *
 * columns: product_id, category_id (FK), name, description (nullable),
 *          price DECIMAL(10,2), stock INT, is_available, image_url (nullable)
 */
public class Product {

    // ── Fields ─────────────────────────────────────────────────────────────
    private int    productId;
    private int    categoryId;
    private String name;
    private String description;   // nullable TEXT
    private double price;
    private int    stock;
    /** Mis à false automatiquement lorsque le stock atteint 0 (KAN-19). */
    private boolean available = true;
    private String imageUrl;      // nullable VARCHAR(300)

    // ── Constructors ────────────────────────────────────────────────────────

    public Product() {}

    public Product(int productId, int categoryId, String name, String description,
                   double price, int stock, boolean available, String imageUrl) {
        this.productId   = productId;
        this.categoryId  = categoryId;
        this.name        = name;
        this.description = description;
        this.price       = price;
        this.stock       = stock;
        this.available   = available;
        this.imageUrl    = imageUrl;
    }

    // ── Getters & Setters ───────────────────────────────────────────────────

    public int    getProductId()          { return productId; }
    public void   setProductId(int v)     { this.productId = v; }

    public int    getCategoryId()          { return categoryId; }
    public void   setCategoryId(int v)     { this.categoryId = v; }

    public String getName()          { return name; }
    public void   setName(String v)  { this.name = v; }

    public String getDescription()          { return description; }
    public void   setDescription(String v)  { this.description = v; }

    public double getPrice()          { return price; }
    public void   setPrice(double v)  { this.price = v; }

    public int    getStock()          { return stock; }
    public void   setStock(int v)     { this.stock = v; }

    public boolean isAvailable()       { return available; }
    public void    setAvailable(boolean v) { this.available = v; }

    public String getImageUrl()          { return imageUrl; }
    public void   setImageUrl(String v)  { this.imageUrl = v; }

    // ── toString ────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "Product{" +
               "productId="    + productId   +
               ", categoryId=" + categoryId  +
               ", name='"      + name        + '\'' +
               ", price="      + price       +
               ", stock="      + stock       +
               ", available="  + available   +
               '}';
    }
}
