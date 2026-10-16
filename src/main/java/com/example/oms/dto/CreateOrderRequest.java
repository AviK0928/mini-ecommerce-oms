package com.example.oms.dto;

import java.util.List;

/** Body of POST /api/orders. Prices and totals are never accepted from the client. */
public record CreateOrderRequest(String customerName, List<OrderItemRequest> items) {
}