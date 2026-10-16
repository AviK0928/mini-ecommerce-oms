package com.example.oms;

import com.example.oms.dto.CreateOrderRequest;
import com.example.oms.dto.CreateProductRequest;
import com.example.oms.dto.OrderDetailResponse;
import com.example.oms.dto.OrderItemRequest;
import com.example.oms.exception.ApiException;
import com.example.oms.model.OrderStatus;
import com.example.oms.service.OrderService;
import com.example.oms.service.ProductService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/test-orders.db?foreign_keys=on")
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");
    }

    // Scenario 1
    @Test
    void orderingThreeOfTenLeavesSeven() {
        long productId = product("Toothbrush", "TB-1", "49.50", 10);

        OrderDetailResponse order = orderService.createOrder(order("Asha", item(productId, 3)));

        assertThat(stockOf(productId)).isEqualTo(7);
        assertThat(order.id()).isPositive();
        assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.totalAmount()).isEqualByComparingTo("148.50");
        assertThat(order.items()).hasSize(1);
        assertThat(order.items().get(0).unitPrice()).isEqualByComparingTo("49.50");
    }

    // Scenario 2
    @Test
    void orderingElevenOfTenIsRejectedAndStockUnchanged() {
        long productId = product("Toothbrush", "TB-1", "49.50", 10);

        assertApiError(HttpStatus.CONFLICT, () -> orderService.createOrder(order("Asha", item(productId, 11))));

        assertThat(stockOf(productId)).isEqualTo(10);
        assertThat(orderCount()).isZero();
    }

    // Scenario 3
    @Test
    void orderIsRejectedEntirelyIfAnyProductHasInsufficientStock() {
        long enough = product("Brush", "B-1", "10", 10);
        long tooFew = product("Floss", "F-1", "20", 2);

        // The valid item comes first, so its deduction must be rolled back.
        assertApiError(HttpStatus.CONFLICT,
                () -> orderService.createOrder(order("Asha", item(enough, 3), item(tooFew, 5))));

        assertThat(stockOf(enough)).isEqualTo(10);
        assertThat(stockOf(tooFew)).isEqualTo(2);
        assertThat(orderCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_items", Integer.class)).isZero();
    }

    // Scenario 4
    @Test
    void cancellingRestoresStockForAllItems() {
        long a = product("Brush", "B-1", "10", 10);
        long b = product("Floss", "F-1", "20", 5);
        OrderDetailResponse order = orderService.createOrder(order("Asha", item(a, 3), item(b, 2)));

        OrderDetailResponse cancelled = orderService.cancelOrder(order.id());

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockOf(a)).isEqualTo(10);
        assertThat(stockOf(b)).isEqualTo(5);
    }

    // Scenario 5
    @Test
    void cancellingTwiceDoesNotRestoreStockTwice() {
        long productId = product("Brush", "B-1", "10", 10);
        OrderDetailResponse order = orderService.createOrder(order("Asha", item(productId, 3)));
        orderService.cancelOrder(order.id());

        assertApiError(HttpStatus.CONFLICT, () -> orderService.cancelOrder(order.id()));

        assertThat(stockOf(productId)).isEqualTo(10);
    }

    // Scenario 6
    @Test
    void zeroNegativeOrMissingQuantitiesAreRejected() {
        long productId = product("Brush", "B-1", "10", 10);

        assertApiError(HttpStatus.BAD_REQUEST, () -> orderService.createOrder(order("Asha", item(productId, 0))));
        assertApiError(HttpStatus.BAD_REQUEST, () -> orderService.createOrder(order("Asha", item(productId, -2))));
        assertApiError(HttpStatus.BAD_REQUEST, () -> orderService.createOrder(order("Asha", item(productId, null))));

        assertThat(stockOf(productId)).isEqualTo(10);
        assertThat(orderCount()).isZero();
    }

    @Test
    void invalidOrderRequestsAreRejected() {
        long productId = product("Brush", "B-1", "10", 10);

        assertApiError(HttpStatus.BAD_REQUEST, () -> orderService.createOrder(order("  ", item(productId, 1))));
        assertApiError(HttpStatus.BAD_REQUEST, () -> orderService.createOrder(new CreateOrderRequest("Asha", List.of())));
        assertApiError(HttpStatus.BAD_REQUEST,
                () -> orderService.createOrder(order("Asha", item(productId, 1), item(productId, 2))));
        assertThat(orderCount()).isZero();
    }

    @Test
    void nonexistentProductCannotBeOrdered() {
        long productId = product("Brush", "B-1", "10", 10);

        assertApiError(HttpStatus.NOT_FOUND,
                () -> orderService.createOrder(order("Asha", item(productId, 1), item(99999L, 1))));

        assertThat(stockOf(productId)).isEqualTo(10);
        assertThat(orderCount()).isZero();
    }

    @Test
    void missingOrderReturnsNotFound() {
        assertApiError(HttpStatus.NOT_FOUND, () -> orderService.getOrder(99999));
        assertApiError(HttpStatus.NOT_FOUND, () -> orderService.cancelOrder(99999));
    }

    @Test
    void orderKeepsOriginalPriceAfterProductPriceChanges() {
        long productId = product("Brush", "B-1", "10.00", 10);
        OrderDetailResponse order = orderService.createOrder(order("Asha", item(productId, 2)));

        jdbc.update("UPDATE products SET price_paise = 99900, name = 'Renamed' WHERE id = ?", productId);

        OrderDetailResponse reloaded = orderService.getOrder(order.id());
        assertThat(reloaded.totalAmount()).isEqualByComparingTo("20.00");
        assertThat(reloaded.items().get(0).unitPrice()).isEqualByComparingTo("10.00");
        assertThat(reloaded.items().get(0).productName()).isEqualTo("Brush");
    }

    @Test
    void concurrentOrdersForLastUnitDoNotOversell() throws Exception {
        long productId = product("Last One", "L-1", "10", 1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            start.await();
            try {
                orderService.createOrder(order("Buyer", item(productId, 1)));
                return true;
            } catch (ApiException e) {
                return false;
            }
        };

        Future<Boolean> first = pool.submit(attempt);
        Future<Boolean> second = pool.submit(attempt);
        start.countDown();
        int successes = (first.get(30, TimeUnit.SECONDS) ? 1 : 0) + (second.get(30, TimeUnit.SECONDS) ? 1 : 0);
        pool.shutdown();

        assertThat(successes).isEqualTo(1);
        assertThat(stockOf(productId)).isZero();
        assertThat(orderCount()).isEqualTo(1);
    }

    // ---- helpers ----

    private long product(String name, String sku, String price, int stock) {
        return productService.createProduct(new CreateProductRequest(name, sku, new BigDecimal(price), stock)).id();
    }

    private static CreateOrderRequest order(String customerName, OrderItemRequest... items) {
        return new CreateOrderRequest(customerName, List.of(items));
    }

    private static OrderItemRequest item(long productId, Integer quantity) {
        return new OrderItemRequest(productId, quantity);
    }

    private int stockOf(long productId) {
        return jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id = ?", Integer.class, productId);
    }

    private int orderCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
    }

    private static void assertApiError(HttpStatus expected, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(expected));
    }
}