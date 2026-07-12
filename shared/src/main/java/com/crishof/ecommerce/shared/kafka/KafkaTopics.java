package com.crishof.ecommerce.shared.kafka;

/**
 * Constantes con los nombres de topics de Kafka y los headers estándar
 * que se propagan entre servicios.
 *
 * Headers estándar:
 *   x-correlation-id  — trazabilidad end-to-end
 *   x-event-type      — nombre corto del tipo (ej "OrderPlaced")
 *   x-source-service  — nombre del servicio emisor
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    public static final String ORDER_EVENTS        = "order-events";
    public static final String INVENTORY_EVENTS    = "inventory-events";
    public static final String PAYMENT_EVENTS      = "payment-events";
    public static final String NOTIFICATION_EVENTS = "notification-events";

    public static final String HEADER_CORRELATION_ID = "x-correlation-id";
    public static final String HEADER_EVENT_TYPE     = "x-event-type";
    public static final String HEADER_SOURCE_SERVICE = "x-source-service";
}
