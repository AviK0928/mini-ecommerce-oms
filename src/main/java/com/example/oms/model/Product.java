package com.example.oms.model;

/** A row of the products table. Price is held in paise. */
public record Product(long id, String name, String sku, long pricePaise, int stockQuantity) {
}