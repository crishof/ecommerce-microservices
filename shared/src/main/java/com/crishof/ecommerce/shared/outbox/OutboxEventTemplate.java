package com.crishof.ecommerce.shared.outbox;

import java.time.LocalDateTime;

/**
 * Record que documenta la forma canónica de una fila de outbox_events.
 * Cada servicio define su propia @Entity mapeada a su tabla; este record
 * existe solo como referencia del patrón outbox compartido.
 */
public record OutboxEventTemplate(
        Long id,
        String aggregateId,
        String aggregateType,
        String eventType,
        String payloadJson,
        LocalDateTime occurredAt,
        LocalDateTime publishedAt,
        String correlationId
) {}
