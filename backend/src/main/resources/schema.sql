CREATE TABLE IF NOT EXISTS products (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    name           TEXT    NOT NULL,
    sku            TEXT    NOT NULL UNIQUE,
    price_paise    INTEGER NOT NULL CHECK (price_paise > 0),
    stock_quantity INTEGER NOT NULL CHECK (stock_quantity >= 0)
);

CREATE TABLE IF NOT EXISTS orders (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_name      TEXT    NOT NULL,
    total_amount_paise INTEGER NOT NULL CHECK (total_amount_paise > 0),
    status             TEXT    NOT NULL CHECK (status IN ('PLACED', 'CANCELLED')),
    created_at         TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS order_items (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id         INTEGER NOT NULL REFERENCES orders (id),
    product_id       INTEGER NOT NULL REFERENCES products (id),
    product_name     TEXT    NOT NULL,
    unit_price_paise INTEGER NOT NULL CHECK (unit_price_paise > 0),
    quantity         INTEGER NOT NULL CHECK (quantity > 0),
    line_total_paise INTEGER NOT NULL CHECK (line_total_paise > 0)
);

CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items (order_id);