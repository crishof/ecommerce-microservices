package com.crishof.ecommerce.payment.event;

import com.crishof.ecommerce.payment.service.PaymentService;
import com.crishof.ecommerce.shared.event.OrderPlacedEvent;
import com.crishof.ecommerce.shared.event.StockReservedEvent;
import com.crishof.ecommerce.shared.kafka.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Consume:
 *  - order-events / OrderPlaced      → registra el cobro pendiente (proyección)
 *  - inventory-events / StockReserved → intenta cobrar (publica PaymentSucceeded/Failed)
 */
@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(PaymentService paymentService, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.ORDER_EVENTS, groupId = "payment-service.order-events")
    public void onOrderEvent(
            @Payload String payload,
            @Header(KafkaTopics.HEADER_EVENT_TYPE) String eventType,
            @Header(name = KafkaTopics.HEADER_CORRELATION_ID, required = false) String correlationId) {

        if (correlationId != null) {
            MDC.put("correlationId", correlationId);
        }
        try {
            if ("OrderPlaced".equals(eventType)) {
                paymentService.registerPending(objectMapper.readValue(payload, OrderPlacedEvent.class));
            } else {
                log.debug("[PAYMENT] Evento order ignorado: {}", eventType);
            }
        } finally {
            MDC.remove("correlationId");
        }
    }

    @KafkaListener(topics = KafkaTopics.INVENTORY_EVENTS, groupId = "payment-service.inventory-events")
    public void onInventoryEvent(
            @Payload String payload,
            @Header(KafkaTopics.HEADER_EVENT_TYPE) String eventType,
            @Header(name = KafkaTopics.HEADER_CORRELATION_ID, required = false) String correlationId) {

        if (correlationId != null) {
            MDC.put("correlationId", correlationId);
        }
        try {
            if ("StockReserved".equals(eventType)) {
                paymentService.charge(objectMapper.readValue(payload, StockReservedEvent.class));
            } else {
                log.debug("[PAYMENT] Evento inventory ignorado: {}", eventType);
            }
        } finally {
            MDC.remove("correlationId");
        }
    }
}
