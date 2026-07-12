package com.crishof.ecommerce.shared.event;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Emitido por inventory-service cuando NO puede reservar el stock (compensación).
 */
public record StockReservationFailedEvent(
        String aggregateId,                 // orderId
        String reason,
        List<String> unavailableProductIds,
        LocalDateTime occurredAt,
        String correlationId
) implements DomainEvent {}
