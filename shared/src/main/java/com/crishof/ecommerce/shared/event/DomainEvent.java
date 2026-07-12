package com.crishof.ecommerce.shared.event;

import java.time.LocalDateTime;

/**
 * Interfaz marker común a todos los eventos de dominio del sistema.
 * Permite tratar cualquier evento de forma genérica en logging, headers, etc.
 */
public interface DomainEvent {

    String aggregateId();

    LocalDateTime occurredAt();

    String correlationId();
}
