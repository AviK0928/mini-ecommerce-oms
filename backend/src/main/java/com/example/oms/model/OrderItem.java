package com.example.oms.model;

/**
 * A row of the order_items table.
 * productName and unitPricePaise are snapshots taken when the order was placed,
 * so order history stays correct even if the product changes later.
 */
public record OrderItem(long id, long orderId, long productId, String productName,
                        long unitPricePaise, int quantity, long lineTotalPaise) {
}