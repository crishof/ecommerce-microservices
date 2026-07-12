package com.crishof.ecommerce.shared.event;

import java.time.LocalDateTime;

/**
 * Emitido por order-service cuando el pedido se cancela (compensación de la saga).
 */
public record OrderCancelledEvent(
        String aggregateId,                 // orderId
        long customerId,
        String customerEmail,
        String reason,
        LocalDateTime occurredAt,
        String correlationId
) implements DomainEvent {}
