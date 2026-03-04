package org.ilynosov.hw_2.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ilynosov.hw_2.api.OrdersApi;
import org.ilynosov.hw_2.entity.Order;
import org.ilynosov.hw_2.model.OrderCreateRequest;
import org.ilynosov.hw_2.model.OrderResponse;
import org.ilynosov.hw_2.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OrdersController implements OrdersApi {

    private final OrderService orderService;

    @Override
    public ResponseEntity<OrderResponse> createOrder(UUID xUserId,
                                                     OrderCreateRequest request) {

        var order = orderService.createOrder(xUserId, request);

        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setStatus(OrderResponse.StatusEnum.fromValue(order.getStatus().name()));
        response.setTotalAmount(order.getTotalAmount());

        return ResponseEntity.status(201).body(response);
    }

    @Override
    public ResponseEntity<OrderResponse> cancelOrder(UUID id, UUID xUserId) {

        var order = orderService.cancelOrder(id, xUserId);

        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setStatus(OrderResponse.StatusEnum.fromValue(order.getStatus().name()));
        response.setTotalAmount(order.getTotalAmount());

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<OrderResponse> updateOrder(UUID id,
                                                     UUID xUserId,
                                                     OrderCreateRequest request) {

        var order = orderService.updateOrder(id, xUserId, request);

        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setStatus(OrderResponse.StatusEnum.fromValue(order.getStatus().name()));
        response.setTotalAmount(order.getTotalAmount());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/debug/orders")
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }
}