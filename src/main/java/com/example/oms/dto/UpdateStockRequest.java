package com.example.oms.dto;

/** Body of PATCH /api/products/{id}/stock. Sets the stock to this absolute value. */
public record UpdateStockRequest(Integer stockQuantity) {
}