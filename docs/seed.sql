USE chrionline;

-- ============================================================================
-- ChriOnline seed data
-- Objectif:
-- - Fournir une base de demo complete pour les ecrans client + admin
-- - Couvrir utilisateurs, catalogue, images, paniers, commandes, paiements,
--   notifications, sessions et login_security
-- - Etre deterministic: le script nettoie les donnees applicatives puis reinsere
--   un jeu coherent de donnees
--
-- Comptes de demo:
-- - admin.master@chrionline.ma  / Admin!2026
-- - akram.client@chrionline.ma / Client!2026
-- - sara.client@chrionline.ma   / Sara!2026
-- - youssef.client@chrionline.ma / Youssef!2026
-- - nadia.client@chrionline.ma  / Nadia!2026
-- - pending.verify@chrionline.ma / Client!2026   (email non verifie)
-- ============================================================================

-- Colonnes ajoutees dynamiquement par le serveur dans certaines executions
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(32) NOT NULL DEFAULT 'CLIENT';
ALTER TABLE users ADD COLUMN IF NOT EXISTS public_key TEXT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_reset_token VARCHAR(64) NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_reset_expires_at DATETIME NULL;
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS role VARCHAR(32) NOT NULL DEFAULT 'CLIENT';

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM payments;
DELETE FROM order_items;
DELETE FROM orders;
DELETE FROM cart_items;
DELETE FROM carts;
DELETE FROM notifications;
DELETE FROM sessions;
DELETE FROM login_security;
DELETE FROM product_images;
DELETE FROM products;
DELETE FROM categories;
DELETE FROM users;

SET FOREIGN_KEY_CHECKS = 1;

ALTER TABLE categories AUTO_INCREMENT = 1;
ALTER TABLE users AUTO_INCREMENT = 1;
ALTER TABLE products AUTO_INCREMENT = 1;
ALTER TABLE product_images AUTO_INCREMENT = 1;
ALTER TABLE carts AUTO_INCREMENT = 1;
ALTER TABLE cart_items AUTO_INCREMENT = 1;
ALTER TABLE orders AUTO_INCREMENT = 1;
ALTER TABLE order_items AUTO_INCREMENT = 1;
ALTER TABLE payments AUTO_INCREMENT = 1;
ALTER TABLE notifications AUTO_INCREMENT = 1;

-- ============================================================================
-- 1. Categories
-- ============================================================================
INSERT INTO categories (category_id, name, description) VALUES
    (1, 'PC Portables', 'Ordinateurs portables pour bureautique, developpement et gaming.'),
    (2, 'Smartphones', 'Smartphones milieu et haut de gamme, 5G et photo.'),
    (3, 'Audio', 'Casques, ecouteurs, enceintes et accessoires audio.'),
    (4, 'Peripheriques', 'Claviers, souris, webcams et accessoires bureau.'),
    (5, 'Stockage', 'SSD, cles USB, disques externes et cartes memoire.');

-- ============================================================================
-- 2. Users
-- ============================================================================
INSERT INTO users (
    user_id, username, email, password_hash, role, public_key, is_suspended,
    is_email_verified, email_verification_code, email_verification_expires_at,
    email_verification_sent_at, trusted_login_ip, login_ip_verification_code,
    login_ip_verification_expires_at, login_ip_verification_sent_at,
    login_ip_verification_pending_ip, password_reset_token, password_reset_expires_at, created_at
) VALUES
    (
        1, 'admin', 'admin.master@chrionline.ma',
        '$2a$12$9LQ1K9Bo4msZkIKUSLaFTed.kkIWZnaE1Vegk7bPvk0RdtcU0UKqu',
        'SUPER_ADMIN', 'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAz+hvYtPcAwUZ7Lwddiz8QBEqPDPAqdM25nidSJQT+o3p17yGRWO7r5RHWwsusXRlBuld8rtdKntRQ3na7sIirgyOkUh81rceLZV/i539ocmtKThqMWDAlCKr6KQaqzHeQcQYqV8h3tv21SOW7sCxFxJa84rJwSUJYFmqVilrfjzHw7VaMS+9V2U0/PM7+Tiu9l/pjHq7y/RpbQATYHZPFxtq9UJfRm9bUtERmfJfV5vXw5sNDlYihp2lXeK+Rt8/F32H0zYLlsOj7ruHywae1uZhgVbonCsFkdbdjzJkzs29oS/KAbmiQ7qFYU15/8grkeuZQ/GETh1Ve29p1IPWCwIDAQAB', FALSE, TRUE, NULL, NULL, NULL,
        '127.0.0.1', NULL, NULL, NULL, NULL, NULL, NULL, '2026-01-05 09:00:00'
    ),
    (
        2, 'akram.client', 'akram.client@chrionline.ma',
        '$2a$12$NZQ7Uuc6CN1XCF05Wcz/4.KFGznp.AJKg4Hz7FxIHBSXkqZcxjJW2',
        'CLIENT', NULL, FALSE, TRUE, NULL, NULL, NULL,
        '127.0.0.1', NULL, NULL, NULL, NULL, NULL, NULL, '2026-01-12 14:20:00'
    ),
    (
        3, 'sara.tech', 'sara.client@chrionline.ma',
        '$2a$12$sWXvdmfRTPltcIpIRHVQBee.PDyTQdj7lXa.JS4nud128TgQSgVu6',
        'CLIENT', NULL, FALSE, TRUE, NULL, NULL, NULL,
        '127.0.0.1', NULL, NULL, NULL, NULL, NULL, NULL, '2026-02-02 11:15:00'
    ),
    (
        4, 'youssef.pro', 'youssef.client@chrionline.ma',
        '$2a$12$Imu6w8rdn0qOjdVfXH1dL.HEJGbdUWlLoIRxxu2i1pFgG9kLVVGkS',
        'CLIENT', NULL, FALSE, TRUE, NULL, NULL, NULL,
        '127.0.0.1', NULL, NULL, NULL, NULL, NULL, NULL, '2026-02-10 18:05:00'
    ),
    (
        5, 'nadia.shop', 'nadia.client@chrionline.ma',
        '$2a$12$xoxTjzzQppK8pklmhg/P4eamyVxCYmRl6GY2YfjSc0997ITr4iFrS',
        'CLIENT', NULL, TRUE, TRUE, NULL, NULL, NULL,
        '127.0.0.1', NULL, NULL, NULL, NULL, NULL, NULL, '2026-03-03 10:30:00'
    )
    ;

-- ============================================================================
-- 3. Products
-- ============================================================================
INSERT INTO products (
    product_id, category_id, name, description, price, stock, is_available, image_url
) VALUES
    (1, 1, 'Lenovo IdeaPad Slim 5', '15.6 pouces FHD, Ryzen 7, 16 Go RAM, SSD 512 Go.', 8299.00, 9, TRUE,  'https://images.unsplash.com/photo-1496181133206-80ce9b88a853'),
    (2, 1, 'ASUS TUF Gaming A15', 'Gaming 144Hz, RTX 4060, 16 Go RAM, SSD 1 To.',      14999.00, 5, TRUE,  'https://images.unsplash.com/photo-1517336714731-489689fd1ca8'),
    (3, 1, 'MacBook Air M3',       'Ultrabook leger, 13 pouces, 8 Go RAM, SSD 256 Go.', 16499.00, 3, TRUE,  'https://images.unsplash.com/photo-1515879218367-8466d910aaa4'),
    (4, 2, 'Samsung Galaxy S24',   '5G, 256 Go, ecran AMOLED, triple capteur photo.',    10999.00, 8, TRUE,  'https://images.unsplash.com/photo-1511707171634-5f897ff02aa9'),
    (5, 2, 'iPhone 15',            'Dynamic Island, 128 Go, USB-C, excellent photo.',    12499.00, 6, TRUE,  'https://images.unsplash.com/photo-1510557880182-3de5a1f7f6b5'),
    (6, 2, 'Xiaomi Redmi Note 13', 'AMOLED 120Hz, 256 Go, charge rapide 67W.',            3199.00, 15, TRUE,  'https://images.unsplash.com/photo-1512499617640-c2f999098c01'),
    (7, 3, 'Sony WH-1000XM5',      'Casque Bluetooth ANC premium, autonomie 30h.',        4299.00, 7, TRUE,  'https://images.unsplash.com/photo-1505740420928-5e560c06d30e'),
    (8, 3, 'JBL Flip 6',           'Enceinte portable Bluetooth, IP67, basses puissantes.', 1499.00, 14, TRUE, 'https://images.unsplash.com/photo-1545454675-3531b543be5d'),
    (9, 4, 'Logitech MX Master 3S','Souris bureautique premium, silencieuse, multi-device.', 1299.00, 12, TRUE, 'https://images.unsplash.com/photo-1527814050087-3793815479db'),
    (10, 4, 'Keychron K8',         'Clavier mecanique sans fil, switches hot-swappable.', 1099.00, 10, TRUE, 'https://images.unsplash.com/photo-1511467687858-23d96c32e4ae'),
    (11, 5, 'Samsung T7 1To',      'SSD externe USB-C tres rapide pour sauvegardes.',      1299.00, 20, TRUE, 'https://images.unsplash.com/photo-1591799265444-d66432b91588'),
    (12, 5, 'Kingston DataTraveler 128Go', 'Cle USB 3.2 compacte pour transport de donnees.', 179.00, 0, FALSE, 'https://images.unsplash.com/photo-1587033411391-5d9e51cce126');

-- ============================================================================
-- 4. Product images
-- ============================================================================
INSERT INTO product_images (product_image_id, product_id, image_url, display_order, is_primary) VALUES
    (1, 1,  'https://images.unsplash.com/photo-1496181133206-80ce9b88a853', 0, TRUE),
    (2, 1,  'https://images.unsplash.com/photo-1515879218367-8466d910aaa4', 1, FALSE),
    (3, 2,  'https://images.unsplash.com/photo-1517336714731-489689fd1ca8', 0, TRUE),
    (4, 2,  'https://images.unsplash.com/photo-1593642702821-c8da6771f0c6', 1, FALSE),
    (5, 3,  'https://images.unsplash.com/photo-1515879218367-8466d910aaa4', 0, TRUE),
    (6, 4,  'https://images.unsplash.com/photo-1511707171634-5f897ff02aa9', 0, TRUE),
    (7, 5,  'https://images.unsplash.com/photo-1510557880182-3de5a1f7f6b5', 0, TRUE),
    (8, 6,  'https://images.unsplash.com/photo-1512499617640-c2f999098c01', 0, TRUE),
    (9, 7,  'https://images.unsplash.com/photo-1505740420928-5e560c06d30e', 0, TRUE),
    (10, 8, 'https://images.unsplash.com/photo-1545454675-3531b543be5d', 0, TRUE),
    (11, 9, 'https://images.unsplash.com/photo-1527814050087-3793815479db', 0, TRUE),
    (12, 10,'https://images.unsplash.com/photo-1511467687858-23d96c32e4ae', 0, TRUE),
    (13, 11,'https://images.unsplash.com/photo-1591799265444-d66432b91588', 0, TRUE),
    (14, 12,'https://images.unsplash.com/photo-1587033411391-5d9e51cce126', 0, TRUE);

-- ============================================================================
-- 5. Carts
-- ============================================================================
INSERT INTO carts (cart_id, user_id, created_at, updated_at) VALUES
    (1, 2, '2026-04-15 18:00:00', '2026-04-16 09:12:00'),
    (2, 3, '2026-04-12 10:05:00', '2026-04-16 10:40:00'),
    (3, 4, '2026-04-10 16:45:00', '2026-04-16 11:25:00'),
    (4, 5, '2026-04-09 13:10:00', '2026-04-10 13:10:00'),
    (5, 1, '2026-04-16 08:59:00', '2026-04-16 08:59:00');

INSERT INTO cart_items (cart_item_id, cart_id, product_id, quantity, unit_price) VALUES
    (1, 1, 7, 1, 4299.00),
    (2, 1, 11, 1, 1299.00),
    (3, 2, 6, 2, 3199.00),
    (4, 2, 9, 1, 1299.00),
    (5, 3, 2, 1, 14999.00),
    (6, 3, 10, 1, 1099.00);

-- ============================================================================
-- 6. Orders
-- ============================================================================
INSERT INTO orders (order_id, user_id, status, total_amount, created_at, updated_at) VALUES
    (1, 2, 'PENDING',   16499.00, '2026-04-12 09:10:00', '2026-04-12 09:10:00'),
    (2, 3, 'VALIDATED', 13397.00, '2026-04-10 14:22:00', '2026-04-10 14:40:00'),
    (3, 4, 'SHIPPED',    5798.00, '2026-04-08 17:05:00', '2026-04-09 08:30:00'),
    (4, 2, 'DELIVERED',  1499.00, '2026-04-02 11:48:00', '2026-04-05 16:45:00'),
    (5, 3, 'CANCELLED',  12499.00,'2026-04-01 10:15:00', '2026-04-01 12:00:00');

INSERT INTO order_items (order_item_id, order_id, product_id, quantity, unit_price) VALUES
    (1, 1, 3, 1, 16499.00),
    (2, 2, 4, 1, 10999.00),
    (3, 2, 9, 1, 1299.00),
    (4, 2, 10, 1, 1099.00),
    (5, 3, 7, 1, 4299.00),
    (6, 3, 11, 1, 1299.00),
    (7, 3, 12, 1, 200.00),
    (8, 4, 8, 1, 1499.00),
    (9, 5, 5, 1, 12499.00);

-- ============================================================================
-- 7. Payments
-- ============================================================================
INSERT INTO payments (payment_id, order_id, method, status, amount, transaction_id, paid_at) VALUES
    (1, 1, 'SIMULATED',   'PENDING', 16499.00, NULL, NULL),
    (2, 2, 'CREDIT_CARD', 'SUCCESS', 13397.00, 'TXN-2026-0002', '2026-04-10 14:39:00'),
    (3, 3, 'CREDIT_CARD', 'SUCCESS',  5798.00, 'TXN-2026-0003', '2026-04-08 17:20:00'),
    (4, 4, 'SIMULATED',   'SUCCESS',  1499.00, 'TXN-2026-0004', '2026-04-02 11:50:00'),
    (5, 5, 'CREDIT_CARD', 'FAILED',  12499.00, 'TXN-2026-0005', NULL);

-- ============================================================================
-- 8. Notifications
-- ============================================================================
INSERT INTO notifications (notification_id, user_id, message, sent_at, is_read) VALUES
    (1, 2, 'Votre commande #1 est en attente de paiement.',                '2026-04-12 09:12:00', FALSE),
    (2, 2, 'Votre commande #4 a ete livree avec succes.',                  '2026-04-05 16:50:00', TRUE),
    (3, 3, 'Votre commande #2 a ete validee et est en preparation.',       '2026-04-10 14:45:00', FALSE),
    (4, 4, 'Votre commande #3 a ete expediee. Suivi disponible.',          '2026-04-09 08:35:00', FALSE),
    (5, 5, 'Paiement refuse pour la commande #5. Veuillez reessayer.',     '2026-04-01 12:02:00', TRUE);
-- ============================================================================
-- 9. Sessions
-- ============================================================================
INSERT INTO sessions (session_id, user_id, role, token, created_at, expires_at, is_active) VALUES
    ('11111111-1111-1111-1111-111111111111', 1, 'SUPER_ADMIN', 'seed-admin-session-token',  '2026-04-16 08:50:00', '2026-04-16 09:00:00', FALSE),
    ('22222222-2222-2222-2222-222222222222', 2, 'CLIENT', 'seed-client-session-token', '2026-04-16 09:00:00', '2026-04-16 09:20:00', TRUE);

-- ============================================================================
-- 10. Login security state
-- ============================================================================
INSERT INTO login_security (
    email, ip_address, failures, window_started_at, blocked_until, ip_blocked_until, updated_at
) VALUES
    ('akram.client@chrionline.ma',  '127.0.0.1',    2, '2026-04-16 08:40:00', NULL, NULL, '2026-04-16 08:45:00'),
    ('sara.client@chrionline.ma',    '192.168.1.20', 4, '2026-04-16 08:30:00', '2026-04-16 09:05:00', NULL, '2026-04-16 08:55:00'),
    ('nadia.client@chrionline.ma',   '172.16.0.13', 12,'2026-04-16 07:50:00', '2026-04-16 09:30:00', '2026-04-16 10:30:00', '2026-04-16 08:59:00');

-- ============================================================================
-- Fin du seed
-- ============================================================================
