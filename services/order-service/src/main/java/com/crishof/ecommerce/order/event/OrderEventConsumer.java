package com.crishof.ecommerce.order.event;

import com.crishof.ecommerce.order.domain.Order;
import com.crishof.ecommerce.order.outbox.OutboxEvent;
import com.crishof.ecommerce.order.outbox.OutboxRepository;
import com.crishof.ecommerce.order.repository.OrderRepository;
import com.crishof.ecommerce.shared.event.OrderCancelledEvent;
import com.crishof.ecommerce.shared.event.OrderConfirmedEvent;
import com.crishof.ecommerce.shared.event.PaymentFailedEvent;
import com.crishof.ecommerce.shared.event.PaymentSucceededEvent;
import com.crishof.ecommerce.shared.event.StockReservationFailedEvent;
import com.crishof.ecommerce.shared.event.StockReservedEvent;
import com.crishof.ecommerce.shared.kafka.KafkaTopics;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumers de order-service. Reaccionan a:
 *  - StockReserved            → status STOCK_RESERVED
 *  - StockReservationFailed   → status CANCELLED + OrderCancelledEvent
 *  - PaymentSucceeded         → status PAID + OrderConfirmedEvent
 *  - PaymentFailed            → status CANCELLED + OrderCancelledEvent
 */
@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(OrderRepository orderRepository,
                              OutboxRepository outboxRepository,
                              ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.INVENTORY_EVENTS, groupId = "order-service.inventory-events")
    @Transactional
    public void onInventoryEvent(
            @Payload String payload,
            @Header(KafkaTopics.HEADER_EVENT_TYPE) String eventType,
            @Header(name = KafkaTopics.HEADER_CORRELATION_ID, required = false) String correlationId) {

        withCorrelation(correlationId, () -> {
            switch (eventType) {
                case "StockReserved" -> handleStockReserved(payload);
                case "StockReservationFailed" -> handleStockReservationFailed(payload);
                default -> log.debug("[ORDER] Evento inventory ignorado: {}", eventType);
            }
        });
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_EVENTS, groupId = "order-service.payment-events")
    @Transactional
    public void onPaymentEvent(
            @Payload String payload,
            @Header(KafkaTopics.HEADER_EVENT_TYPE) String eventType,
            @Header(name = KafkaTopics.HEADER_CORRELATION_ID, required = false) String correlationId) {

        withCorrelation(correlationId, () -> {
            switch (eventType) {
                case "PaymentSucceeded" -> handlePaymentSucceeded(payload);
                case "PaymentFailed" -> handlePaymentFailed(payload);
                default -> log.debug("[ORDER] Evento payment ignorado: {}", eventType);
            }
        });
    }

    private void handleStockReserved(String payload) {
        StockReservedEvent event = objectMapper.readValue(payload, StockReservedEvent.class);
        Order order = load(event.aggregateId());
        order.setStatus("STOCK_RESERVED");
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        log.info("[ORDER] Order {} -> STOCK_RESERVED", event.aggregateId());
    }

    private void handleStockReservationFailed(String payload) {
        StockReservationFailedEvent event =
                objectMapper.readValue(payload, StockReservationFailedEvent.class);
        Order order = load(event.aggregateId());
        order.setStatus("CANCELLED");
        order.setFailureReason("Stock insuficiente: " + event.reason());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        publishOrderCancelled(order, event.reason(), event.correlationId());
        log.warn("[ORDER] Order {} -> CANCELLED por stock: {}", event.aggregateId(), event.reason());
    }

    private void handlePaymentSucceeded(String payload) {
        PaymentSucceededEvent event = objectMapper.readValue(payload, PaymentSucceededEvent.class);
        Order order = load(event.orderId());
        order.setStatus("PAID");
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        publishOrderConfirmed(order, event.correlationId());
        log.info("[ORDER] Order {} -> PAID", event.orderId());
    }

    private void handlePaymentFailed(String payload) {
        PaymentFailedEvent event = objectMapper.readValue(payload, PaymentFailedEvent.class);
        Order order = load(event.orderId());
        order.setStatus("CANCELLED");
        order.setFailureReason("Pago rechazado: " + event.reason());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        publishOrderCancelled(order, event.reason(), event.correlationId());
        log.warn("[ORDER] Order {} -> CANCELLED por pago: {}", event.orderId(), event.reason());
    }

    private Order load(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));
    }

    private void publishOrderConfirmed(Order order, String correlationId) {
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                order.getId(), order.getCustomerId(), order.getCustomerEmail(),
                order.getTotalAmount(), LocalDateTime.now(), correlationId);
        saveToOutbox(order.getId(), "OrderConfirmed", event, correlationId);
    }

    private void publishOrderCancelled(Order order, String reason, String correlationId) {
        OrderCancelledEvent event = new OrderCancelledEvent(
                order.getId(), order.getCustomerId(), order.getCustomerEmail(),
                reason, LocalDateTime.now(), correlationId);
        saveToOutbox(order.getId(), "OrderCancelled", event, correlationId);
    }

    private void saveToOutbox(String aggregateId, String eventType, Object event, String correlationId) {
        OutboxEvent outbox = new OutboxEvent();
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setTopic(KafkaTopics.ORDER_EVENTS);
        outbox.setPayloadJson(objectMapper.writeValueAsString(event));
        outbox.setCorrelationId(correlationId);
        outbox.setCreatedAt(LocalDateTime.now());
        outboxRepository.save(outbox);
    }

    private void withCorrelation(String correlationId, Runnable action) {
        if (correlationId != null) {
            MDC.put("correlationId", correlationId);
        }
        try {
            action.run();
        } finally {
            MDC.remove("correlationId");
        }
    }
}
