package com.crishof.ecommerce.inventory.event;

import com.crishof.ecommerce.inventory.service.InventoryService;
import com.crishof.ecommerce.shared.event.OrderCancelledEvent;
import com.crishof.ecommerce.shared.event.OrderPlacedEvent;
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
 * Consume order-events:
 *  - OrderPlaced    → reserva stock (publica StockReserved)
 *  - OrderCancelled → libera stock reservado (compensación)
 */
@Component
public class InventoryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    public InventoryEventConsumer(InventoryService inventoryService, ObjectMapper objectMapper) {
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.ORDER_EVENTS, groupId = "inventory-service.order-events")
    public void onOrderEvent(
            @Payload String payload,
            @Header(KafkaTopics.HEADER_EVENT_TYPE) String eventType,
            @Header(name = KafkaTopics.HEADER_CORRELATION_ID, required = false) String correlationId) {

        if (correlationId != null) {
            MDC.put("correlationId", correlationId);
        }
        try {
            switch (eventType) {
                case "OrderPlaced" ->
                        inventoryService.reserve(objectMapper.readValue(payload, OrderPlacedEvent.class));
                case "OrderCancelled" -> {
                    OrderCancelledEvent event = objectMapper.readValue(payload, OrderCancelledEvent.class);
                    inventoryService.release(event.aggregateId(), event.correlationId());
                }
                default -> log.debug("[INVENTORY] Evento order ignorado: {}", eventType);
            }
        } finally {
            MDC.remove("correlationId");
        }
    }
}
