# Mini E-commerce Order Management System

A small full-stack application where an admin can manage products and inventory, place
multi-product customer orders, and cancel orders, with all data stored in a local SQLite database.

## Features

- **Products & inventory**: add products (name, SKU, price, stock), view them in a table, update stock.
- **Create order**: customer name plus multiple products with quantities, and a live price/total preview.
- **Orders**: list (ID, customer, total, status, created time), detail view with items, and cancellation.
- **All-or-nothing orders**: if any item is invalid or short on stock, the whole order is rejected
  and no stock changes.
- **Safe cancellation**: restores stock for every item, exactly once.
- **Persistence**: data survives page refreshes and server restarts.
- Duplicate SKUs are rejected (case-insensitive).
- Concurrent orders for the last unit cannot oversell.
- Automated tests cover order creation and cancellation.

## Tech stack

| Layer    | Technology                                              |
|----------|---------------------------------------------------------|
| Backend  | Java 21, Spring Boot 3.5 (Spring Web, Spring JDBC)       |
| Database | SQLite (single local file, via `sqlite-jdbc`)           |
| Frontend | HTML, CSS, vanilla JavaScript (`fetch`), served by Spring Boot |
| Build    | Maven                                                   |
| Tests    | JUnit 5, Spring Boot Test                               |

No separate database server, frontend server or internet connection is needed to run it.

## Requirements

- **JDK 21** or newer (`java -version`)
- **Maven 3.9** or newer (`mvn -version`)

## Run locally

```bash
git clone https://github.com/AviK0928/mini-ecommerce-oms.git
cd mini-ecommerce-oms
mvn spring-boot:run
```

Open **http://localhost:8080** in a browser.

The first start downloads dependencies (about a minute). Stop the server with `Ctrl+C`.

To run as a standalone JAR instead:

```bash
mvn package
java -jar target/mini-ecommerce-oms-0.0.1-SNAPSHOT.jar
```

If port 8080 is busy:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

## Run tests

```bash
mvn test
```

The tests use their own database files under `target/`, so they never touch your data.

## Database

- **Location:** `data/shop.db`, relative to the folder the app is started from. The folder and
  file are created automatically on first start.
- **Schema:** `src/main/resources/schema.sql` runs on every start. It only uses
  `CREATE ... IF NOT EXISTS`, so existing data is never modified.
- **Backup:** stop the app and copy `data/shop.db`. **Reset:** stop the app and delete `data/shop.db`.
- The database file is excluded from Git.

### Schema

```
products     (id PK, name, sku UNIQUE, price_paise, stock_quantity CHECK >= 0)
orders       (id PK, customer_name, total_amount_paise, status CHECK IN ('PLACED','CANCELLED'), created_at)
order_items  (id PK, order_id FK -> orders, product_id FK -> products,
              product_name, unit_price_paise, quantity CHECK > 0, line_total_paise)
```

- **Money** is stored as whole paise (`INTEGER`) to avoid floating-point errors. The API uses
  rupee decimals such as `49.50`.
- **Order items store a snapshot** of the product name and unit price, so order history stays
  correct if a product changes later.
- Foreign keys are enforced (`foreign_keys=on` in the JDBC URL).

## API

All endpoints accept and return JSON. Errors have the shape `{ "error": "message" }`.

| Method | Path                        | Description                           | Success | Errors        |
|--------|-----------------------------|---------------------------------------|---------|---------------|
| GET    | `/api/products`             | List products                         | 200     |               |
| POST   | `/api/products`             | Create product                        | 201     | 400, 409      |
| PATCH  | `/api/products/{id}/stock`  | Set stock quantity                    | 200     | 400, 404      |
| GET    | `/api/orders`               | List orders (newest first)            | 200     |               |
| GET    | `/api/orders/{id}`          | Order detail with items               | 200     | 404           |
| POST   | `/api/orders`               | Place order                           | 201     | 400, 404, 409 |
| POST   | `/api/orders/{id}/cancel`   | Cancel a placed order                 | 200     | 404, 409      |

The status codes mean:
- **400**: invalid input, such as a missing field, a zero or negative quantity, or a bad price.
- **404**: the product or order doesn't exist.
- **409**: insufficient stock, a duplicate SKU, or an order that is already cancelled.

Example requests:

```json
POST /api/products
{ "name": "Toothbrush", "sku": "TB-001", "price": 49.50, "stockQuantity": 10 }

POST /api/orders
{ "customerName": "Asha", "items": [ { "productId": 1, "quantity": 3 } ] }
```

The client sends only product IDs and quantities. Prices and totals are always calculated on the
server from database values.

## How consistency is guaranteed

**Placing an order** runs in a single database transaction:

1. Validate the request: customer name, at least one item, positive whole quantities, no
   duplicate products.
2. For each item, deduct stock with one conditional statement:
   `UPDATE products SET stock_quantity = stock_quantity - ? WHERE id = ? AND stock_quantity >= ?`
   - The check and the deduction are atomic, so stock can never go negative.
   - If 0 rows are updated, stock was insufficient. The service throws and the **whole
     transaction rolls back**, including deductions already made for earlier items.
3. Insert the order and its items.

A `CHECK (stock_quantity >= 0)` constraint is a final safety net at the database level.

**Cancelling an order** also runs in one transaction:

1. `UPDATE orders SET status = 'CANCELLED' WHERE id = ? AND status = 'PLACED'`
2. If no row changes, the order is missing (404) or already cancelled (409), and stock is not touched.
3. Otherwise, each item's quantity is added back to its product.

The status update can succeed only once, so stock is never restored twice.

**Concurrency:** the connection pool uses a single connection (SQLite allows one writer at a time),
so transactions run one after another. Two simultaneous orders for the last unit result in exactly
one success. A test covers this.

## Project structure

```
src/main/java/com/example/oms/
  controller/   REST endpoints (HTTP only)
  service/      business rules, validation, transactions
  repository/   SQL via JdbcTemplate (parameterised queries)
  model/        domain records, OrderStatus, Money helper
  dto/          request/response objects
  exception/    ApiException + global JSON error handler
src/main/resources/
  application.properties, schema.sql
  static/       index.html, css/styles.css, js/app.js
src/test/java/com/example/oms/
  ProductServiceTest, OrderServiceTest
```

## Known limitations

- No authentication; this is a single-admin local tool.
- The single database connection serialises all requests. That is fine for local use, but it
  would limit throughput under heavy load.
- Products cannot be edited (other than stock) or deleted.
- No pagination, search or filtering.
- The database path is relative to the start-up folder: starting the app from a different
  folder uses a different `data/shop.db`.

## Possible improvements

- Inventory movement history (stock additions, order deductions, cancellation restocks).
- Product search and order status filter.
- Move to PostgreSQL with row-level locking for multi-user deployments.
- Pagination for large product and order lists.