package com.example.oms;

import com.example.oms.dto.CreateProductRequest;
import com.example.oms.dto.ProductResponse;
import com.example.oms.dto.UpdateStockRequest;
import com.example.oms.exception.ApiException;
import com.example.oms.service.ProductService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/test-products.db?foreign_keys=on")
class ProductServiceTest {

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

    @Test
    void foreignKeysAreEnabled() {
        assertThat(jdbc.queryForObject("PRAGMA foreign_keys", Integer.class)).isEqualTo(1);
    }

    @Test
    void createsAndListsProduct() {
        ProductResponse created = productService.createProduct(request("Toothbrush", "tb-001", "49.50", 10));

        assertThat(created.id()).isPositive();
        assertThat(created.sku()).isEqualTo("TB-001");
        assertThat(created.price()).isEqualByComparingTo("49.50");
        assertThat(created.stockQuantity()).isEqualTo(10);
        assertThat(productService.listProducts()).containsExactly(created);
    }

    @Test
    void rejectsMissingOrBlankName() {
        assertApiError(HttpStatus.BAD_REQUEST, () -> productService.createProduct(request(null, "A1", "10", 1)));
        assertApiError(HttpStatus.BAD_REQUEST, () -> productService.createProduct(request("   ", "A1", "10", 1)));
    }

    @Test
    void rejectsInvalidPrice() {
        assertApiError(HttpStatus.BAD_REQUEST, () -> productService.createProduct(request("P", "A1", null, 1)));
        assertApiError(HttpStatus.BAD_REQUEST, () -> productService.createProduct(request("P", "A1", "0", 1)));
        assertApiError(HttpStatus.BAD_REQUEST, () -> productService.createProduct(request("P", "A1", "-5", 1)));
        assertApiError(HttpStatus.BAD_REQUEST, () -> productService.createProduct(request("P", "A1", "1.999", 1)));
        assertThat(productService.listProducts()).isEmpty();
    }

    @Test
    void rejectsNegativeStock() {
        assertApiError(HttpStatus.BAD_REQUEST, () -> productService.createProduct(request("P", "A1", "10", -1)));
    }

    @Test
    void rejectsDuplicateSkuIgnoringCase() {
        productService.createProduct(request("Brush", "SKU-1", "10", 5));

        assertApiError(HttpStatus.CONFLICT, () -> productService.createProduct(request("Other", "sku-1", "20", 5)));
        assertThat(productService.listProducts()).hasSize(1);
    }

    @Test
    void updatesStock() {
        ProductResponse created = productService.createProduct(request("Floss", "FL-1", "25", 10));

        ProductResponse updated = productService.updateStock(created.id(), new UpdateStockRequest(25));

        assertThat(updated.stockQuantity()).isEqualTo(25);
    }

    @Test
    void rejectsInvalidStockUpdate() {
        ProductResponse created = productService.createProduct(request("Floss", "FL-1", "25", 10));

        assertApiError(HttpStatus.BAD_REQUEST, () -> productService.updateStock(created.id(), new UpdateStockRequest(-1)));
        assertApiError(HttpStatus.BAD_REQUEST, () -> productService.updateStock(created.id(), new UpdateStockRequest(null)));
        assertApiError(HttpStatus.NOT_FOUND, () -> productService.updateStock(99999, new UpdateStockRequest(5)));
        assertThat(productService.listProducts().get(0).stockQuantity()).isEqualTo(10);
    }

    private static CreateProductRequest request(String name, String sku, String price, Integer stock) {
        return new CreateProductRequest(name, sku, price == null ? null : new BigDecimal(price), stock);
    }

    private static void assertApiError(HttpStatus expected, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(expected));
    }
}