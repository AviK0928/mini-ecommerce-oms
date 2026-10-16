package com.example.oms.dto;

import com.example.oms.model.Money;
import com.example.oms.model.Order;
import com.example.oms.model.OrderStatus;

import java.math.BigDecimal;

/** One row of the order list (GET /api/orders). */
public record OrderSummaryResponse(long id, String customerName, BigDecimal totalAmount,
                                   OrderStatus status, String createdAt) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.id(),
                order.customerName(),
                Money.toRupees(order.totalAmountPaise()),
                order.status(),
                order.createdAt());
    }
}