package com.crishof.ecommerce.order.service;

import com.crishof.ecommerce.order.client.CatalogClient;
import com.crishof.ecommerce.order.client.IdentityClient;
import com.crishof.ecommerce.order.domain.Order;
import com.crishof.ecommerce.order.outbox.OutboxEvent;
import com.crishof.ecommerce.order.outbox.OutboxRepository;
import com.crishof.ecommerce.order.repository.OrderRepository;
import com.crishof.ecommerce.shared.event.OrderPlacedEvent;
import com.crishof.ecommerce.shared.event.OrderPlacedEvent.OrderLineData;
import com.crishof.ecommerce.shared.kafka.KafkaTopics;
import feign.FeignException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Lógica de aplicación de order-service.
 *
 * NO orquesta la saga en un solo método: solo la INICIA guardando el
 * OrderPlacedEvent en el outbox. El resto de pasos ocurren asíncronamente
 * vía consumers de eventos (choreography).
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final IdentityClient identityClient;
    private final CatalogClient catalogClient;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository,
                        OutboxRepository outboxRepository,
                        IdentityClient identityClient,
                        CatalogClient catalogClient,
                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.identityClient = identityClient;
        this.catalogClient = catalogClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public String placeOrder(long customerId, List<Map<String, Object>> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("El pedido debe tener al menos una línea");
        }
        String orderId = UUID.randomUUID().toString();
        String correlationId = currentCorrelationId();
        LocalDateTime now = LocalDateTime.now();

        // 1. Validar cliente vía identity-service (SÍNCRONO)
        Map<String, Object> customer;
        try {
            customer = identityClient.findById(customerId);
        } catch (FeignException.NotFound e) {
            throw new IllegalArgumentException("Customer not found: " + customerId);
        }
        String customerEmail = String.valueOf(customer.get("email"));
        String customerName = String.valueOf(customer.get("name"));

        // 2. Obtener productos vía catalog-service (SÍNCRONO, batch)
        List<Long> productIds = lines.stream()
                .map(l -> Long.parseLong(String.valueOf(l.get("productId"))))
                .toList();
        Map<Long, Map<String, Object>> productsById = catalogClient.findByIds(productIds).stream()
                .collect(Collectors.toMap(
                        p -> ((Number) p.get("id")).longValue(),
                        p -> p));

        // 3. Validar que todos existen + 4. construir snapshots
        List<OrderLineData> snapshotLines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int totalItems = 0;
        for (Map<String, Object> line : lines) {
            long pid = Long.parseLong(String.valueOf(line.get("productId")));
            int qty = ((Number) line.get("quantity")).intValue();
            if (qty <= 0) {
                throw new IllegalArgumentException("Cantidad inválida para producto " + pid);
            }
            Map<String, Object> product = productsById.get(pid);
            if (product == null) {
                throw new IllegalArgumentException("Product not found: " + pid);
            }
            BigDecimal price = new BigDecimal(String.valueOf(product.get("price")));
            snapshotLines.add(new OrderLineData(
                    String.valueOf(pid),
                    String.valueOf(product.get("name")),
                    price,
                    qty));
            total = total.add(price.multiply(BigDecimal.valueOf(qty)));
            totalItems += qty;
        }

        // 5. Guardar Order en estado PENDING
        Order order = new Order();
        order.setId(orderId);
        order.setCustomerId(customerId);
        order.setCustomerEmail(customerEmail);
        order.setCustomerName(customerName);
        order.setStatus("PENDING");
        order.setTotalAmount(total);
        order.setLinesJson(objectMapper.writeValueAsString(snapshotLines));
        order.setTotalItems(totalItems);
        order.setDistinctProducts(lines.size());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderRepository.save(order);

        // 6. Guardar OrderPlacedEvent en outbox (misma transacción)
        OrderPlacedEvent event = new OrderPlacedEvent(
                orderId, customerId, customerEmail, customerName,
                snapshotLines, total, now, correlationId);

        OutboxEvent outbox = new OutboxEvent();
        outbox.setAggregateId(orderId);
        outbox.setEventType("OrderPlaced");
        outbox.setTopic(KafkaTopics.ORDER_EVENTS);
        outbox.setPayloadJson(objectMapper.writeValueAsString(event));
        outbox.setCorrelationId(correlationId);
        outbox.setCreatedAt(now);
        outboxRepository.save(outbox);

        log.info("[ORDER] Pedido {} creado en estado PENDING, evento en outbox", orderId);
        return orderId;
    }

    private String currentCorrelationId() {
        String current = MDC.get("correlationId");
        return (current == null || current.isBlank()) ? UUID.randomUUID().toString() : current;
    }
}
