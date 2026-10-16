package com.example.oms.controller;

import com.example.oms.dto.CreateProductRequest;
import com.example.oms.dto.ProductResponse;
import com.example.oms.dto.UpdateStockRequest;
import com.example.oms.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductResponse> list() {
        return productService.listProducts();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@RequestBody CreateProductRequest request) {
        return productService.createProduct(request);
    }

    @PatchMapping("/{id}/stock")
    public ProductResponse updateStock(@PathVariable long id, @RequestBody UpdateStockRequest request) {
        return productService.updateStock(id, request);
    }
}