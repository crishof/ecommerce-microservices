package com.crishof.ecommerce.shared.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Emitido por order-service cuando el pedido queda confirmado (PAID).
 */
public record OrderConfirmedEvent(
        String aggregateId,                 // orderId
        long customerId,
        String customerEmail,
        BigDecimal totalAmount,
        LocalDateTime occurredAt,
        String correlationId
) implements DomainEvent {}
