package com.example.oms.dto;

import com.example.oms.model.Money;
import com.example.oms.model.Order;
import com.example.oms.model.OrderItem;
import com.example.oms.model.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

/** A full order with its items (create, get-by-id and cancel responses). */
public record OrderDetailResponse(long id, String customerName, BigDecimal totalAmount,
                                  OrderStatus status, String createdAt, List<OrderItemResponse> items) {

    public static OrderDetailResponse from(Order order, List<OrderItem> items) {
        return new OrderDetailResponse(
                order.id(),
                order.customerName(),
                Money.toRupees(order.totalAmountPaise()),
                order.status(),
                order.createdAt(),
                items.stream().map(OrderItemResponse::from).toList());
    }
}