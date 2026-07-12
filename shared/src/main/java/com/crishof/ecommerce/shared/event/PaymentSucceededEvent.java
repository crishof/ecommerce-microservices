package com.crishof.ecommerce.shared.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Emitido por payment-service cuando el cobro tiene éxito.
 */
public record PaymentSucceededEvent(
        String aggregateId,                 // paymentId
        String orderId,
        long customerId,
        BigDecimal amount,
        String transactionId,
        LocalDateTime occurredAt,
        String correlationId
) implements DomainEvent {}
