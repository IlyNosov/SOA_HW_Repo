-- =========================
-- ENUM TYPES
-- =========================

CREATE TYPE order_status AS ENUM (
    'CREATED',
    'PAYMENT_PENDING',
    'PAID',
    'SHIPPED',
    'COMPLETED',
    'CANCELED'
);

CREATE TYPE discount_type AS ENUM (
    'PERCENTAGE',
    'FIXED_AMOUNT'
);

CREATE TYPE operation_type AS ENUM (
    'CREATE_ORDER',
    'UPDATE_ORDER'
);

-- =========================
-- PROMO CODES
-- =========================

CREATE TABLE promo_codes (
                             id UUID PRIMARY KEY,
                             code VARCHAR(20) UNIQUE NOT NULL,

                             discount_type discount_type NOT NULL,
                             discount_value DECIMAL(12,2) NOT NULL,

                             min_order_amount DECIMAL(12,2),

                             max_uses INTEGER NOT NULL,
                             current_uses INTEGER DEFAULT 0,

                             valid_from TIMESTAMP NOT NULL,
                             valid_until TIMESTAMP NOT NULL,

                             active BOOLEAN DEFAULT TRUE
);

-- =========================
-- ORDERS
-- =========================

CREATE TABLE orders (
                        id UUID PRIMARY KEY,

                        user_id UUID NOT NULL,

                        status order_status NOT NULL,

                        promo_code_id UUID,
                        total_amount DECIMAL(12,2) NOT NULL,
                        discount_amount DECIMAL(12,2) DEFAULT 0,

                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                        CONSTRAINT fk_orders_promo
                            FOREIGN KEY (promo_code_id)
                                REFERENCES promo_codes(id)
);

CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);

-- =========================
-- ORDER ITEMS
-- =========================

CREATE TABLE order_items (
                             id UUID PRIMARY KEY,

                             order_id UUID NOT NULL,
                             product_id UUID NOT NULL,

                             quantity INTEGER NOT NULL,
                             price_at_order DECIMAL(12,2) NOT NULL,

                             CONSTRAINT fk_items_order
                                 FOREIGN KEY (order_id)
                                     REFERENCES orders(id)
                                     ON DELETE CASCADE,

                             CONSTRAINT fk_items_product
                                 FOREIGN KEY (product_id)
                                     REFERENCES products(id)
);

CREATE INDEX idx_order_items_order ON order_items(order_id);

-- =========================
-- USER OPERATIONS
-- =========================

CREATE TABLE user_operations (
                                 id UUID PRIMARY KEY,

                                 user_id UUID NOT NULL,

                                 operation_type operation_type NOT NULL,

                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_operations_user ON user_operations(user_id);
CREATE INDEX idx_user_operations_type ON user_operations(operation_type);