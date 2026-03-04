-- ============================================================
--  ChriOnline E-Commerce — MySQL Schema
--  Matches the UML class diagram
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
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id)
);

-- ─────────────────────────────────────────
--  3. SESSIONS
-- ─────────────────────────────────────────
CREATE TABLE sessions (
    session_id  VARCHAR(64)   NOT NULL,
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

-- ─────────────────────────────────────────
--  5. CARTS
-- ─────────────────────────────────────────
CREATE TABLE carts (
    cart_id     INT      NOT NULL AUTO_INCREMENT,
    user_id     INT      NOT NULL UNIQUE,       -- one cart per user
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                         ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (cart_id),
    CONSTRAINT fk_cart_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE
);

-- ─────────────────────────────────────────
--  6. CART ITEMS
-- ─────────────────────────────────────────
CREATE TABLE cart_items (
    cart_item_id  INT            NOT NULL AUTO_INCREMENT,
    cart_id       INT            NOT NULL,
    product_id    INT            NOT NULL,
    quantity      INT            NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price    DECIMAL(10,2)  NOT NULL,       -- price snapshot at add time

    PRIMARY KEY (cart_item_id),
    UNIQUE KEY uq_cart_product (cart_id, product_id),  -- no duplicate products in cart
    CONSTRAINT fk_cartitem_cart
        FOREIGN KEY (cart_id) REFERENCES carts(cart_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_cartitem_product
        FOREIGN KEY (product_id) REFERENCES products(product_id)
        ON DELETE RESTRICT
);

-- ─────────────────────────────────────────
--  7. ORDERS
-- ─────────────────────────────────────────
CREATE TABLE orders (
    order_id      VARCHAR(36)    NOT NULL,       -- UUID e.g. "ORD-550e8400-..."
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

-- ─────────────────────────────────────────
--  8. ORDER ITEMS
-- ─────────────────────────────────────────
CREATE TABLE order_items (
    order_item_id  INT            NOT NULL AUTO_INCREMENT,
    order_id       VARCHAR(36)    NOT NULL,
    product_id     INT            NOT NULL,
    quantity       INT            NOT NULL CHECK (quantity > 0),
    unit_price     DECIMAL(10,2)  NOT NULL,      -- price snapshot at order time

    PRIMARY KEY (order_item_id),
    CONSTRAINT fk_orderitem_order
        FOREIGN KEY (order_id) REFERENCES orders(order_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_orderitem_product
        FOREIGN KEY (product_id) REFERENCES products(product_id)
        ON DELETE RESTRICT
);

-- ─────────────────────────────────────────
--  9. PAYMENTS
-- ─────────────────────────────────────────
CREATE TABLE payments (
    payment_id      INT            NOT NULL AUTO_INCREMENT,
    order_id        VARCHAR(36)    NOT NULL UNIQUE,   -- one payment per order
    method          ENUM('CREDIT_CARD','SIMULATED') NOT NULL,
    status          ENUM('PENDING','SUCCESS','FAILED') NOT NULL DEFAULT 'PENDING',
    amount          DECIMAL(10,2)  NOT NULL,
    transaction_id  VARCHAR(100)   UNIQUE,            -- generated after processing
    paid_at         DATETIME,

    PRIMARY KEY (payment_id),
    CONSTRAINT fk_payment_order
        FOREIGN KEY (order_id) REFERENCES orders(order_id)
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
