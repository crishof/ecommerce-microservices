package com.crishof.ecommerce.order.controller;

import com.crishof.ecommerce.order.service.OrderService;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestBody Map<String, Object> request,
            UriComponentsBuilder uriBuilder) {

        long customerId = ((Number) request.get("customerId")).longValue();
        List<Map<String, Object>> lines = (List<Map<String, Object>>) request.get("lines");

        String orderId = orderService.placeOrder(customerId, lines);
        URI location = uriBuilder.path("/api/orders/{id}").buildAndExpand(orderId).toUri();

        // 202 Accepted: la saga se ejecuta asíncronamente
        return ResponseEntity.accepted().location(location).body(Map.of(
                "orderId", orderId,
                "status", "PENDING",
                "message", "Pedido aceptado, procesando saga en background"));
    }
}
