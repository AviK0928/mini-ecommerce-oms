package com.example.oms.dto;

/** One line of POST /api/orders. Only the product id and quantity are trusted from the client. */
public record OrderItemRequest(Long productId, Integer quantity) {
}