CREATE TYPE product_status AS ENUM ('ACTIVE','INACTIVE','ARCHIVED');

CREATE TABLE products (
                          id UUID PRIMARY KEY,
                          name VARCHAR(255) NOT NULL,
                          description VARCHAR(4000),
                          price DECIMAL(12,2) NOT NULL CHECK (price > 0),
                          stock INTEGER NOT NULL CHECK (stock >= 0),
                          category VARCHAR(100) NOT NULL,
                          status product_status NOT NULL,
                          seller_id UUID,
                          created_at TIMESTAMP NOT NULL DEFAULT now(),
                          updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_products_status ON products(status);
CREATE INDEX idx_products_category ON products(category);