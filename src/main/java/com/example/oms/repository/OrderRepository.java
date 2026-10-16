package com.example.oms.repository;

import com.example.oms.model.Order;
import com.example.oms.model.OrderItem;
import com.example.oms.model.OrderStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** SQL access for the orders and order_items tables. All queries are parameterised. */
@Repository
public class OrderRepository {

    private static final String ORDER_COLUMNS = "id, customer_name, total_amount_paise, status, created_at";
    private static final String ITEM_COLUMNS =
            "id, order_id, product_id, product_name, unit_price_paise, quantity, line_total_paise";

    private static final RowMapper<Order> ORDER_MAPPER = (rs, rowNum) -> new Order(
            rs.getLong("id"),
            rs.getString("customer_name"),
            rs.getLong("total_amount_paise"),
            OrderStatus.valueOf(rs.getString("status")),
            rs.getString("created_at"));

    private static final RowMapper<OrderItem> ITEM_MAPPER = (rs, rowNum) -> new OrderItem(
            rs.getLong("id"),
            rs.getLong("order_id"),
            rs.getLong("product_id"),
            rs.getString("product_name"),
            rs.getLong("unit_price_paise"),
            rs.getInt("quantity"),
            rs.getLong("line_total_paise"));

    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** All orders, newest first. */
    public List<Order> findAll() {
        return jdbc.query("SELECT " + ORDER_COLUMNS + " FROM orders ORDER BY id DESC", ORDER_MAPPER);
    }

    public Optional<Order> findById(long id) {
        return jdbc.query("SELECT " + ORDER_COLUMNS + " FROM orders WHERE id = ?", ORDER_MAPPER, id)
                .stream()
                .findFirst();
    }

    public List<OrderItem> findItems(long orderId) {
        return jdbc.query("SELECT " + ITEM_COLUMNS + " FROM order_items WHERE order_id = ? ORDER BY id",
                ITEM_MAPPER, orderId);
    }

    /**
     * Inserts an order and returns its generated id.
     * Must run inside a transaction so last_insert_rowid() uses the same connection as the INSERT.
     */
    public long insertOrder(String customerName, long totalAmountPaise, OrderStatus status, String createdAt) {
        jdbc.update("INSERT INTO orders (customer_name, total_amount_paise, status, created_at) VALUES (?, ?, ?, ?)",
                customerName, totalAmountPaise, status.name(), createdAt);
        Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        if (id == null) {
            throw new IllegalStateException("Could not read generated order id");
        }
        return id;
    }

    public void insertItem(long orderId, long productId, String productName,
                           long unitPricePaise, int quantity, long lineTotalPaise) {
        jdbc.update("INSERT INTO order_items (order_id, product_id, product_name, unit_price_paise, quantity, line_total_paise) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                orderId, productId, productName, unitPricePaise, quantity, lineTotalPaise);
    }

    /**
     * Changes PLACED to CANCELLED. The status condition makes this a one-time transition:
     * a second call updates 0 rows, so stock can never be restored twice.
     */
    public int markCancelled(long orderId) {
        return jdbc.update("UPDATE orders SET status = 'CANCELLED' WHERE id = ? AND status = 'PLACED'", orderId);
    }
}