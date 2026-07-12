package com.crishof.ecommerce.shared.event;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Emitido por inventory-service cuando logra reservar el stock de un pedido.
 */
public record StockReservedEvent(
        String aggregateId,                 // orderId
        List<ReservationData> reservations,
        LocalDateTime occurredAt,
        String correlationId
) implements DomainEvent {

    public record ReservationData(String productId, int quantity) {}
}
