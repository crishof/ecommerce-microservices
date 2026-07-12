package com.crishof.ecommerce.shared.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Emitido por payment-service cuando el cobro es rechazado (compensación).
 */
public record PaymentFailedEvent(
        String aggregateId,                 // paymentId
        String orderId,
        long customerId,
        BigDecimal amount,
        String reason,
        LocalDateTime occurredAt,
        String correlationId
) implements DomainEvent {}
