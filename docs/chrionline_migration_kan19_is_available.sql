-- KAN-19 — Ajout is_available sur products (bases déjà créées sans cette colonne)
USE chrionline;

ALTER TABLE products
    ADD COLUMN is_available BOOLEAN NOT NULL DEFAULT TRUE
    AFTER stock;

UPDATE products SET is_available = (stock > 0);
