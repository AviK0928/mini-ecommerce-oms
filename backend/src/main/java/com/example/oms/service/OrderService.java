package com.example.oms.service;

import com.example.oms.dto.CreateOrderRequest;
import com.example.oms.dto.OrderDetailResponse;
import com.example.oms.dto.OrderItemRequest;
import com.example.oms.dto.OrderSummaryResponse;
import com.example.oms.exception.ApiException;
import com.example.oms.model.Order;
import com.example.oms.model.OrderItem;
import com.example.oms.model.OrderStatus;
import com.example.oms.model.Product;
import com.example.oms.repository.OrderRepository;
import com.example.oms.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Order business rules: validation, all-or-nothing stock deduction, and one-time cancellation. */
@Service
public class OrderService {

    static final int MAX_CUSTOMER_NAME_LENGTH = 100;
    static final int MAX_QUANTITY = 1_000_000;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    public List<OrderSummaryResponse> listOrders() {
        return orderRepository.findAll().stream()
                .map(OrderSummaryResponse::from)
                .toList();
    }

    public OrderDetailResponse getOrder(long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("Order " + orderId + " not found"));
        return OrderDetailResponse.from(order, orderRepository.findItems(orderId));
    }

    /**
     * Places an order. Runs in one transaction: if any item fails (missing product,
     * insufficient stock), the exception rolls back every stock deduction already made,
     * so no partial order or partial stock change is ever saved.
     */
    @Transactional
    public OrderDetailResponse createOrder(CreateOrderRequest request) {
        if (request == null) {
            throw ApiException.badRequest("Request body is required");
        }
        String customerName = validateCustomerName(request.customerName());
        List<OrderItemRequest> items = validateItems(request.items());

        List<PricedLine> lines = new ArrayList<>();
        long totalPaise = 0;
        for (OrderItemRequest item : items) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> ApiException.notFound("Product " + item.productId() + " not found"));

            if (productRepository.decreaseStock(product.id(), item.quantity()) == 0) {
                throw ApiException.conflict("Insufficient stock for " + product.sku()
                        + ": requested " + item.quantity() + ", available " + product.stockQuantity());
            }

            // Price comes from the database, never from the client.
            long lineTotal = Math.multiplyExact(product.pricePaise(), item.quantity());
            totalPaise = Math.addExact(totalPaise, lineTotal);
            lines.add(new PricedLine(product, item.quantity(), lineTotal));
        }

        String createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        long orderId = orderRepository.insertOrder(customerName, totalPaise, OrderStatus.PLACED, createdAt);
        for (PricedLine line : lines) {
            orderRepository.insertItem(orderId, line.product().id(), line.product().name(),
                    line.product().pricePaise(), line.quantity(), line.lineTotal());
        }
        return getOrder(orderId);
    }

    /**
     * Cancels a PLACED order and restores its stock, in one transaction.
     * The conditional status update guarantees stock is restored at most once.
     */
    @Transactional
    public OrderDetailResponse cancelOrder(long orderId) {
        if (orderRepository.markCancelled(orderId) == 0) {
            Order existing = orderRepository.findById(orderId)
                    .orElseThrow(() -> ApiException.notFound("Order " + orderId + " not found"));
            throw ApiException.conflict("Order " + orderId + " cannot be cancelled because it is already "
                    + existing.status());
        }
        for (OrderItem item : orderRepository.findItems(orderId)) {
            productRepository.increaseStock(item.productId(), item.quantity());
        }
        return getOrder(orderId);
    }

    private static String validateCustomerName(String customerName) {
        if (customerName == null || customerName.isBlank()) {
            throw ApiException.badRequest("Customer name is required");
        }
        String trimmed = customerName.trim();
        if (trimmed.length() > MAX_CUSTOMER_NAME_LENGTH) {
            throw ApiException.badRequest("Customer name must be at most " + MAX_CUSTOMER_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    /** Checks every item before any database change is made. */
    private static List<OrderItemRequest> validateItems(List<OrderItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw ApiException.badRequest("Order must contain at least one item");
        }
        Set<Long> seenProductIds = new HashSet<>();
        for (OrderItemRequest item : items) {
            if (item == null || item.productId() == null) {
                throw ApiException.badRequest("Each item must have a productId");
            }
            if (item.quantity() == null) {
                throw ApiException.badRequest("Quantity is required for product " + item.productId());
            }
            if (item.quantity() <= 0) {
                throw ApiException.badRequest("Quantity must be greater than 0 for product " + item.productId());
            }
            if (item.quantity() > MAX_QUANTITY) {
                throw ApiException.badRequest("Quantity cannot exceed " + MAX_QUANTITY);
            }
            if (!seenProductIds.add(item.productId())) {
                throw ApiException.badRequest("Product " + item.productId() + " appears more than once in the order");
            }
        }
        return items;
    }

    /** An order line priced from the database, waiting to be saved once the order id exists. */
    private record PricedLine(Product product, int quantity, long lineTotal) {
    }
}