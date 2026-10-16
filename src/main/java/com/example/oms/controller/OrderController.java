package com.example.oms.controller;

import com.example.oms.dto.CreateOrderRequest;
import com.example.oms.dto.OrderDetailResponse;
import com.example.oms.dto.OrderSummaryResponse;
import com.example.oms.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderSummaryResponse> list() {
        return orderService.listOrders();
    }

    @GetMapping("/{id}")
    public OrderDetailResponse get(@PathVariable long id) {
        return orderService.getOrder(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDetailResponse create(@RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @PostMapping("/{id}/cancel")
    public OrderDetailResponse cancel(@PathVariable long id) {
        return orderService.cancelOrder(id);
    }
}