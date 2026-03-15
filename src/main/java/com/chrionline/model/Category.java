package com.chrionline.model;

/**
 * Maps to the {@code categories} table.
 *
 * columns: category_id (PK), name, description (nullable TEXT)
 */
public class Category {

    // ── Fields ─────────────────────────────────────────────────────────────
    private int    categoryId;
    private String name;
    private String description;   // nullable in schema

    // ── Constructors ────────────────────────────────────────────────────────

    public Category() {}

    public Category(int categoryId, String name, String description) {
        this.categoryId  = categoryId;
        this.name        = name;
        this.description = description;
    }

    // ── Getters & Setters ───────────────────────────────────────────────────

    public int    getCategoryId()          { return categoryId; }
    public void   setCategoryId(int v)     { this.categoryId = v; }

    public String getName()          { return name; }
    public void   setName(String v)  { this.name = v; }

    public String getDescription()          { return description; }
    public void   setDescription(String v)  { this.description = v; }

    // ── toString ────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "Category{" +
               "categoryId="    + categoryId  +
               ", name='"       + name        + '\'' +
               ", description='" + description + '\'' +
               '}';
    }
}
