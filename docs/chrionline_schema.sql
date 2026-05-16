-- ============================================================
--  ChriOnline E-Commerce — MySQL Schema
--  Matches the UML class diagram
--  v2 - Added: auto-increment integer orders
-- ============================================================

CREATE DATABASE IF NOT EXISTS chrionline
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE chrionline;

-- ─────────────────────────────────────────
--  1. CATEGORIES
-- ─────────────────────────────────────────
CREATE TABLE categories (
    category_id   INT           NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100)  NOT NULL,
    description   TEXT,

    PRIMARY KEY (category_id)
);

-- ─────────────────────────────────────────
--  2. USERS
-- ─────────────────────────────────────────
CREATE TABLE users (
    user_id       INT           NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)   NOT NULL UNIQUE,
    email         VARCHAR(150)  NOT NULL UNIQUE,
    password_hash VARCHAR(255)  NOT NULL,
    role          ENUM('CLIENT','ADMIN_PENDING','ADMIN','SUPER_ADMIN') NOT NULL DEFAULT 'CLIENT',
    public_key    TEXT NULL,
    is_suspended  BOOLEAN       NOT NULL DEFAULT FALSE,
    is_email_verified BOOLEAN   NOT NULL DEFAULT FALSE,
    email_verification_code VARCHAR(16),
    email_verification_expires_at DATETIME,
    email_verification_sent_at DATETIME,
    trusted_login_ip VARCHAR(64),
    login_ip_verification_code VARCHAR(16),
    login_ip_verification_expires_at DATETIME,
    login_ip_verification_sent_at DATETIME,
    login_ip_verification_pending_ip VARCHAR(64),
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id)
    -- NOTE: UNIQUE on email and username already act as indexes
);

-- ─────────────────────────────────────────
--  3. SESSIONS
--  Source of truth for session persistence.
--  Abderrahim's HashMap is a runtime cache —
--  this table is the authoritative store,
--  enabling sessions to survive server restarts.
-- ─────────────────────────────────────────
CREATE TABLE sessions (
    session_id  VARCHAR(36)   NOT NULL,           -- plain UUID (java.util.UUID)
    user_id     INT           NOT NULL,
    role        ENUM('CLIENT','ADMIN_PENDING','ADMIN','SUPER_ADMIN') NOT NULL DEFAULT 'CLIENT',
    token       VARCHAR(255)  NOT NULL UNIQUE,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at  DATETIME      NOT NULL,
    is_active   BOOLEAN       NOT NULL DEFAULT TRUE,

    PRIMARY KEY (session_id),
    CONSTRAINT fk_session_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE
);

-- Fast token lookup — called on every single TCP request via isTokenValid()
CREATE INDEX idx_session_token  ON sessions(token);
CREATE INDEX idx_session_user   ON sessions(user_id);
CREATE INDEX idx_session_role   ON sessions(role);

-- ─────────────────────────────────────────
--  4. PRODUCTS
-- ─────────────────────────────────────────
CREATE TABLE products (
    product_id   INT            NOT NULL AUTO_INCREMENT,
    category_id  INT            NOT NULL,
    name         VARCHAR(150)   NOT NULL,
    description  TEXT,
    price        DECIMAL(10,2)  NOT NULL CHECK (price >= 0),
    stock        INT            NOT NULL DEFAULT 0 CHECK (stock >= 0),
    is_available BOOLEAN        NOT NULL DEFAULT TRUE,
    image_url    VARCHAR(300),

    PRIMARY KEY (product_id),
    CONSTRAINT fk_product_category
        FOREIGN KEY (category_id) REFERENCES categories(category_id)
        ON DELETE RESTRICT
);

-- Category filter on the product catalogue
CREATE INDEX idx_product_category ON products(category_id);

--  4.b PRODUCT IMAGES
--  Multiple images per product with one primary image.
CREATE TABLE product_images (
    product_image_id INT           NOT NULL AUTO_INCREMENT,
    product_id       INT           NOT NULL,
    image_url        VARCHAR(300)  NOT NULL,
    display_order    INT           NOT NULL DEFAULT 0,
    is_primary       BOOLEAN       NOT NULL DEFAULT FALSE,

    PRIMARY KEY (product_image_id),
    CONSTRAINT fk_productimage_product
        FOREIGN KEY (product_id) REFERENCES products(product_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_productimage_product ON product_images(product_id);

-- ─────────────────────────────────────────
--  5. CARTS
-- ─────────────────────────────────────────
CREATE TABLE carts (
    cart_id     INT      NOT NULL AUTO_INCREMENT,
    user_id     INT      NOT NULL UNIQUE,          -- one cart per user
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                         ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (cart_id),
    CONSTRAINT fk_cart_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE
    -- NOTE: UNIQUE on user_id already acts as an index
);

-- ─────────────────────────────────────────
--  6. CART ITEMS
-- ─────────────────────────────────────────
CREATE TABLE cart_items (
    cart_item_id  INT            NOT NULL AUTO_INCREMENT,
    cart_id       INT            NOT NULL,
    product_id    INT            NOT NULL,
    quantity      INT            NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price    DECIMAL(10,2)  NOT NULL,          -- price snapshot at add time

    PRIMARY KEY (cart_item_id),
    UNIQUE KEY uq_cart_product (cart_id, product_id),   -- no duplicate products in cart
    CONSTRAINT fk_cartitem_cart
        FOREIGN KEY (cart_id) REFERENCES carts(cart_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_cartitem_product
        FOREIGN KEY (product_id) REFERENCES products(product_id)
        ON DELETE RESTRICT
    -- NOTE: uq_cart_product composite unique already covers cart_id lookups
);

-- ─────────────────────────────────────────
--  7. ORDERS
-- ─────────────────────────────────────────
CREATE TABLE orders (
    order_id      INT            NOT NULL AUTO_INCREMENT,
    user_id       INT            NOT NULL,
    status        ENUM('PENDING','VALIDATED','SHIPPED','DELIVERED','CANCELLED')
                                 NOT NULL DEFAULT 'PENDING',
    total_amount  DECIMAL(10,2)  NOT NULL,
    created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
                                 ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (order_id),
    CONSTRAINT fk_order_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE RESTRICT
);

-- Order history queries filtered by user
CREATE INDEX idx_order_user ON orders(user_id);

-- ─────────────────────────────────────────
--  8. ORDER ITEMS
-- ─────────────────────────────────────────
CREATE TABLE order_items (
    order_item_id  INT            NOT NULL AUTO_INCREMENT,
    order_id       INT            NOT NULL,
    product_id     INT            NOT NULL,
    quantity       INT            NOT NULL CHECK (quantity > 0),
    unit_price     DECIMAL(10,2)  NOT NULL,         -- price snapshot at order time

    PRIMARY KEY (order_item_id),
    CONSTRAINT fk_orderitem_order
        FOREIGN KEY (order_id) REFERENCES orders(order_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_orderitem_product
        FOREIGN KEY (product_id) REFERENCES products(product_id)
        ON DELETE RESTRICT
);

-- JOIN orders + order_items + products for order history (KAN-20)
CREATE INDEX idx_orderitem_order   ON order_items(order_id);
CREATE INDEX idx_orderitem_product ON order_items(product_id);

-- ─────────────────────────────────────────
--  9. PAYMENTS
-- ─────────────────────────────────────────
CREATE TABLE payments (
    payment_id      INT            NOT NULL AUTO_INCREMENT,
    order_id        INT            NOT NULL UNIQUE,  -- one payment per order
    method          ENUM('CREDIT_CARD','SIMULATED') NOT NULL,
    status          ENUM('PENDING','SUCCESS','FAILED') NOT NULL DEFAULT 'PENDING',
    amount          DECIMAL(10,2)  NOT NULL,
    transaction_id  VARCHAR(100)   UNIQUE,            -- generated after processing
    paid_at         DATETIME,

    PRIMARY KEY (payment_id),
    CONSTRAINT fk_payment_order
        FOREIGN KEY (order_id) REFERENCES orders(order_id)
        ON DELETE CASCADE
    -- NOTE: UNIQUE on order_id already acts as an index
);

CREATE TABLE payment_cards (
    card_id               INT          NOT NULL AUTO_INCREMENT,
    user_id               INT          NOT NULL,
    brand                 VARCHAR(30)  NOT NULL,
    last4                 CHAR(4)      NOT NULL,
    expiry                CHAR(5)      NOT NULL,
    encrypted_card_number TEXT         NOT NULL,
    card_iv               VARCHAR(64)  NOT NULL,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (card_id),
    UNIQUE KEY uq_payment_card_user_last4_expiry (user_id, last4, expiry),
    INDEX idx_payment_cards_user (user_id),
    CONSTRAINT fk_payment_card_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE
);

-- ─────────────────────────────────────────
--  10. NOTIFICATIONS
-- ─────────────────────────────────────────
CREATE TABLE notifications (
    notification_id  INT          NOT NULL AUTO_INCREMENT,
    user_id          INT          NOT NULL,
    message          TEXT         NOT NULL,
    sent_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_read          BOOLEAN      NOT NULL DEFAULT FALSE,

    PRIMARY KEY (notification_id),
    CONSTRAINT fk_notif_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE
);

-- Fetch unread notifications per user
CREATE INDEX idx_notif_user ON notifications(user_id);

-- ─────────────────────────────────────────
-- Runtime logs are now written by Log4j2 to logs/chrionline.log

-- —————————————————————————————————————————
--  11. LOGIN SECURITY
--  Persists anti-bruteforce state across client/server restarts.
-- —————————————————————————————————————————
CREATE TABLE IF NOT EXISTS login_security (
    email            VARCHAR(150) NOT NULL,
    ip_address       VARCHAR(64)  NOT NULL,
    failures         INT          NOT NULL DEFAULT 0,
    window_started_at DATETIME    NULL,
    blocked_until    DATETIME     NULL,
    ip_blocked_until DATETIME     NULL,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                  ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (email, ip_address)
);

CREATE INDEX idx_login_security_ip ON login_security(ip_address);
CREATE INDEX idx_login_security_ip_blocked ON login_security(ip_address, ip_blocked_until);

