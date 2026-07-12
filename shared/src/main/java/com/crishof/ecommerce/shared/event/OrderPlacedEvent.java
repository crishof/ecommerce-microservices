package com.crishof.ecommerce.shared.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Evento emitido por order-service cuando un pedido se acepta.
 * Contiene snapshots (email, nombre, líneas) para que los consumidores
 * sean autónomos y no tengan que llamar de vuelta a otros servicios.
 */
public record OrderPlacedEvent(
        String aggregateId,                 // orderId (UUID)
        long customerId,
        String customerEmail,               // snapshot para autonomía
        String customerName,
        List<OrderLineData> lines,
        BigDecimal totalAmount,
        LocalDateTime occurredAt,
        String correlationId
) implements DomainEvent {

    public record OrderLineData(
            String productId,
            String productName,
            BigDecimal unitPrice,
            int quantity
    ) {}
}
