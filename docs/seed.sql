
-- ─────────────────────────────────────────
--  SEED DATA: CATEGORIES + PRODUCTS
--  Provides sample catalogue content for the JavaFX client (Home screen).
--  Safe to run multiple times (ON DUPLICATE KEY UPDATE).
-- ─────────────────────────────────────────

INSERT INTO categories (category_id, name, description) VALUES
    (1, 'PC Portables', 'Ordinateurs portables pour travail et gaming'),
    (2, 'Smartphones',  'Téléphones et accessoires mobiles'),
    (3, 'Accessoires',  'Périphériques, audio, stockage et gadgets')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description);

INSERT INTO products (product_id, category_id, name, description, price, stock, is_available, image_url) VALUES
    (1, 1, 'Lenovo IdeaPad 3', '15.6\" FHD · i5 · 8GB RAM · 512GB SSD', 6499.00, 12, TRUE, 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8'),
    (2, 1, 'ASUS TUF Gaming', 'RTX · 16GB RAM · 1TB SSD · 144Hz',      11999.00, 7, TRUE, 'https://images.unsplash.com/photo-1593642532400-2682810df593'),
    (3, 1, 'MacBook Air M2', '13\" · M2 · 8GB RAM · 256GB SSD',        13999.00, 5, TRUE, 'https://images.unsplash.com/photo-1515879218367-8466d910aaa4'),

    (4, 2, 'Samsung Galaxy S23', '128GB · AMOLED · 5G',                 8999.00, 10, TRUE, 'https://images.unsplash.com/photo-1511707171634-5f897ff02aa9'),
    (5, 2, 'iPhone 14',          '128GB · iOS · 5G',                     10999.00, 6, TRUE, 'https://images.unsplash.com/photo-1510557880182-3de5a1f7f6b5'),
    (6, 2, 'Xiaomi Redmi Note',  '128GB · 120Hz · 5000mAh',              2499.00, 18, TRUE, 'https://images.unsplash.com/photo-1512499617640-c2f999098c01'),

    (7, 3, 'Casque Bluetooth',   'ANC · 30h autonomie · USB-C',           799.00, 25, TRUE, 'https://images.unsplash.com/photo-1518441316475-3b9f3f7ab5b5'),
    (8, 3, 'Souris Gaming',      'RGB · 16000 DPI · 6 boutons',           249.00, 40, TRUE, 'https://images.unsplash.com/photo-1527814050087-3793815479db'),
    (9, 3, 'Clavier Mécanique',  'Switches Blue · AZERTY · RGB',          399.00, 15, TRUE, 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8')
ON DUPLICATE KEY UPDATE
    category_id  = VALUES(category_id),
    name         = VALUES(name),
    description  = VALUES(description),
    price        = VALUES(price),
    stock        = VALUES(stock),
    is_available = VALUES(is_available),
    image_url    = VALUES(image_url);
