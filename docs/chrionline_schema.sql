-- ============================================================
--  ChriOnline E-Commerce — MySQL Schema
--  Matches the UML class diagram
--  v2 — Added: server_logs table · indexes · plain UUID orders
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
    role          ENUM('CLIENT','ADMIN') NOT NULL DEFAULT 'CLIENT',
    is_suspended  BOOLEAN       NOT NULL DEFAULT FALSE,
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
    image_url    VARCHAR(300),

    PRIMARY KEY (product_id),
    CONSTRAINT fk_product_category
        FOREIGN KEY (category_id) REFERENCES categories(category_id)
        ON DELETE RESTRICT
);

-- Category filter on the product catalogue
CREATE INDEX idx_product_category ON products(category_id);

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
    order_id      CHAR(36)       NOT NULL,          -- plain UUID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
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
    order_id       CHAR(36)       NOT NULL,
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
    order_id        CHAR(36)       NOT NULL UNIQUE,  -- one payment per order
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
--  11. SERVER LOGS  (KAN-41)
--  Persists all server-side actions for the
--  admin dashboard. user_id is nullable because
--  some events occur before authentication
--  (e.g. failed login attempt, server start).
-- ─────────────────────────────────────────
CREATE TABLE server_logs (
    log_id      INT          NOT NULL AUTO_INCREMENT,
    user_id     INT,                                  -- NULL = pre-auth or system event
    action      VARCHAR(100) NOT NULL,                -- e.g. LOGIN, PLACE_ORDER, PAYMENT
    status      ENUM('SUCCESS','ERROR') NOT NULL,
    message     TEXT,                                 -- error detail or context
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (log_id),
    CONSTRAINT fk_log_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE SET NULL                            -- keep logs even if user is deleted
);

-- Filter logs by user or time range in admin dashboard
CREATE INDEX idx_log_user       ON server_logs(user_id);
CREATE INDEX idx_log_created_at ON server_logs(created_at);

-- ─────────────────────────────────────────
--  DEFAULT ADMIN ACCOUNT
--  Password hash below = BCrypt of "admin1234"
--  Change before any real deployment.
-- ─────────────────────────────────────────
INSERT INTO users (username, email, password_hash, role)
VALUES (
    'admin',
    'admin@chrionline.ma',
    '$2a$12$placeholderHashReplaceWithRealBCryptHash',
    'ADMIN'
);
