package com.example.oms.repository;

import com.example.oms.model.Product;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** SQL access for the products table. All queries are parameterised. */
@Repository
public class ProductRepository {

    private static final String COLUMNS = "id, name, sku, price_paise, stock_quantity";

    private static final RowMapper<Product> ROW_MAPPER = (rs, rowNum) -> new Product(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("sku"),
            rs.getLong("price_paise"),
            rs.getInt("stock_quantity"));

    private final JdbcTemplate jdbc;

    public ProductRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Product> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM products ORDER BY id", ROW_MAPPER);
    }

    public Optional<Product> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM products WHERE id = ?", ROW_MAPPER, id)
                .stream()
                .findFirst();
    }

    public boolean existsBySku(String sku) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE sku = ?", Integer.class, sku);
        return count != null && count > 0;
    }

    /**
     * Inserts a product and returns its generated id.
     * Must run inside a transaction so last_insert_rowid() uses the same connection as the INSERT.
     */
    public long insert(String name, String sku, long pricePaise, int stockQuantity) {
        jdbc.update("INSERT INTO products (name, sku, price_paise, stock_quantity) VALUES (?, ?, ?, ?)",
                name, sku, pricePaise, stockQuantity);
        Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        if (id == null) {
            throw new IllegalStateException("Could not read generated product id");
        }
        return id;
    }

    /** Sets stock to an absolute value. Returns the number of rows updated (0 = product not found). */
    public int updateStock(long id, int stockQuantity) {
        return jdbc.update("UPDATE products SET stock_quantity = ? WHERE id = ?", stockQuantity, id);
    }

    /**
     * Atomically deducts stock only if enough is available.
     * The check and the deduction happen in one SQL statement, so stock can never go negative.
     * Returns 1 on success, 0 if stock was insufficient (or the product does not exist).
     */
    public int decreaseStock(long id, int quantity) {
        return jdbc.update(
                "UPDATE products SET stock_quantity = stock_quantity - ? WHERE id = ? AND stock_quantity >= ?",
                quantity, id, quantity);
    }

    /** Adds stock back, used when an order is cancelled. */
    public int increaseStock(long id, int quantity) {
        return jdbc.update("UPDATE products SET stock_quantity = stock_quantity + ? WHERE id = ?", quantity, id);
    }
}