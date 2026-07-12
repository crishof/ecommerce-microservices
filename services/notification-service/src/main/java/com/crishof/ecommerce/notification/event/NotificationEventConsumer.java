package com.crishof.ecommerce.notification.event;

import com.crishof.ecommerce.notification.service.NotificationService;
import com.crishof.ecommerce.shared.event.OrderCancelledEvent;
import com.crishof.ecommerce.shared.event.OrderConfirmedEvent;
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
 * Consume order-events y registra notificaciones:
 *  - OrderConfirmed → notificación de éxito
 *  - OrderCancelled → notificación de fallo
 */
@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public NotificationEventConsumer(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.ORDER_EVENTS, groupId = "notification-service.order-events")
    public void onOrderEvent(
            @Payload String payload,
            @Header(KafkaTopics.HEADER_EVENT_TYPE) String eventType,
            @Header(name = KafkaTopics.HEADER_CORRELATION_ID, required = false) String correlationId) {

        if (correlationId != null) {
            MDC.put("correlationId", correlationId);
        }
        try {
            switch (eventType) {
                case "OrderConfirmed" ->
                        notificationService.onOrderConfirmed(
                                objectMapper.readValue(payload, OrderConfirmedEvent.class));
                case "OrderCancelled" ->
                        notificationService.onOrderCancelled(
                                objectMapper.readValue(payload, OrderCancelledEvent.class));
                default -> log.debug("[NOTIFICATION] Evento order ignorado: {}", eventType);
            }
        } finally {
            MDC.remove("correlationId");
        }
    }
}
