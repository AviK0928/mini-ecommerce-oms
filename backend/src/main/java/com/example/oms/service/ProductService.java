package com.example.oms.service;

import com.example.oms.dto.CreateProductRequest;
import com.example.oms.dto.ProductResponse;
import com.example.oms.dto.UpdateStockRequest;
import com.example.oms.exception.ApiException;
import com.example.oms.model.Money;
import com.example.oms.model.Product;
import com.example.oms.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/** Product business rules and validation. */
@Service
public class ProductService {

    static final int MAX_NAME_LENGTH = 100;
    static final int MAX_SKU_LENGTH = 50;
    static final int MAX_STOCK = 1_000_000;
    static final BigDecimal MAX_PRICE = new BigDecimal("1000000.00");

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<ProductResponse> listProducts() {
        return productRepository.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        if (request == null) {
            throw ApiException.badRequest("Request body is required");
        }
        String name = requireText(request.name(), "Product name", MAX_NAME_LENGTH);
        String sku = requireText(request.sku(), "SKU", MAX_SKU_LENGTH).toUpperCase(Locale.ROOT);
        long pricePaise = validatePrice(request.price());
        int stock = validateStock(request.stockQuantity());

        if (productRepository.existsBySku(sku)) {
            throw ApiException.conflict("A product with SKU '" + sku + "' already exists");
        }
        long id = productRepository.insert(name, sku, pricePaise, stock);
        return ProductResponse.from(new Product(id, name, sku, pricePaise, stock));
    }

    @Transactional
    public ProductResponse updateStock(long productId, UpdateStockRequest request) {
        if (request == null) {
            throw ApiException.badRequest("Request body is required");
        }
        int stock = validateStock(request.stockQuantity());
        if (productRepository.updateStock(productId, stock) == 0) {
            throw ApiException.notFound("Product " + productId + " not found");
        }
        return ProductResponse.from(productRepository.findById(productId).orElseThrow());
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw ApiException.badRequest(field + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    private static long validatePrice(BigDecimal price) {
        if (price == null) {
            throw ApiException.badRequest("Price is required");
        }
        if (price.signum() <= 0) {
            throw ApiException.badRequest("Price must be greater than 0");
        }
        if (price.stripTrailingZeros().scale() > 2) {
            throw ApiException.badRequest("Price can have at most 2 decimal places");
        }
        if (price.compareTo(MAX_PRICE) > 0) {
            throw ApiException.badRequest("Price cannot exceed " + MAX_PRICE);
        }
        return Money.toPaise(price);
    }

    private static int validateStock(Integer stock) {
        if (stock == null) {
            throw ApiException.badRequest("Stock quantity is required");
        }
        if (stock < 0) {
            throw ApiException.badRequest("Stock quantity cannot be negative");
        }
        if (stock > MAX_STOCK) {
            throw ApiException.badRequest("Stock quantity cannot exceed " + MAX_STOCK);
        }
        return stock;
    }
}