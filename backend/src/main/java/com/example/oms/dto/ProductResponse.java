package com.example.oms.dto;

import com.example.oms.model.Money;
import com.example.oms.model.Product;

import java.math.BigDecimal;

/** Product as returned by the API, with price in rupees. */
public record ProductResponse(long id, String name, String sku, BigDecimal price, int stockQuantity) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.id(),
                product.name(),
                product.sku(),
                Money.toRupees(product.pricePaise()),
                product.stockQuantity());
    }
}