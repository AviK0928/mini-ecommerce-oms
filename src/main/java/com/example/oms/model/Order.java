package com.example.oms.model;

/** A row of the orders table. createdAt is an ISO-8601 UTC timestamp, e.g. 2026-10-16T10:00:00Z. */
public record Order(long id, String customerName, long totalAmountPaise, OrderStatus status, String createdAt) {
}