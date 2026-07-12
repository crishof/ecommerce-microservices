package com.crishof.ecommerce.order.controller;

import com.crishof.ecommerce.order.domain.Order;
import com.crishof.ecommerce.order.repository.OrderRepository;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CASO 3: historial paginado de pedidos de un cliente (read model local).
 */
@RestController
@RequestMapping("/api/customers/{customerId}/orders")
public class CustomerOrderController {

    private final OrderRepository orderRepository;

    public CustomerOrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> findByCustomer(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<Order> orders = orderRepository
                .findByCustomerIdOrderByCreatedAtDesc(customerId, PageRequest.of(page, size));

        return ResponseEntity.ok(Map.of(
                "content", orders.getContent().stream().map(this::toMap).toList(),
                "page", orders.getNumber(),
                "size", orders.getSize(),
                "totalElements", orders.getTotalElements(),
                "totalPages", orders.getTotalPages()));
    }

    private Map<String, Object> toMap(Order order) {
        return Map.of(
                "id", order.getId(),
                "status", order.getStatus(),
                "totalAmount", order.getTotalAmount(),
                "totalItems", order.getTotalItems(),
                "distinctProducts", order.getDistinctProducts(),
                "createdAt", order.getCreatedAt());
    }
}
