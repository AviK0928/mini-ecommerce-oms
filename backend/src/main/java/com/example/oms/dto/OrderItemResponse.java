package com.example.oms.dto;

import com.example.oms.model.Money;
import com.example.oms.model.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(long productId, String productName, int quantity,
                                BigDecimal unitPrice, BigDecimal lineTotal) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.productId(),
                item.productName(),
                item.quantity(),
                Money.toRupees(item.unitPricePaise()),
                Money.toRupees(item.lineTotalPaise()));
    }
}