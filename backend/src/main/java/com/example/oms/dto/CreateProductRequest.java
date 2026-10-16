package com.example.oms.dto;

import java.math.BigDecimal;

/** Body of POST /api/products. Wrapper types let the service detect missing fields (null). */
public record CreateProductRequest(String name, String sku, BigDecimal price, Integer stockQuantity) {
}