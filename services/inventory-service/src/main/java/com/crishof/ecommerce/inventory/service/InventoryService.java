package com.crishof.ecommerce.inventory.service;

import com.crishof.ecommerce.inventory.domain.StockReservation;
import com.crishof.ecommerce.inventory.outbox.OutboxEvent;
import com.crishof.ecommerce.inventory.outbox.OutboxRepository;
import com.crishof.ecommerce.inventory.repository.StockReservationRepository;
import com.crishof.ecommerce.shared.event.OrderPlacedEvent;
import com.crishof.ecommerce.shared.event.StockReservedEvent;
import com.crishof.ecommerce.shared.event.StockReservedEvent.ReservationData;
import com.crishof.ecommerce.shared.kafka.KafkaTopics;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Reserva y libera stock de forma virtual.
 *
 * COMPROMISO DE DISEÑO (académico): inventory-service NO conoce el stock real
 * canónico (que vive en catalog-service); solo mantiene las RESERVAS. En este
 * proyecto la reserva SIEMPRE tiene éxito (se asume stock suficiente). El fallo
 * de la saga se demuestra vía payment-service (failure-rate). La compensación
 * de inventory se ejerce al liberar reservas cuando el pedido se cancela.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final StockReservationRepository reservationRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public InventoryService(StockReservationRepository reservationRepository,
                            OutboxRepository outboxRepository,
                            ObjectMapper objectMapper) {
        this.reservationRepository = reservationRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void reserve(OrderPlacedEvent event) {
        String orderId = event.aggregateId();
        if (reservationRepository.existsByOrderId(orderId)) {
            log.info("[INVENTORY] Reservas para order {} ya existen, ignorando duplicado", orderId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<ReservationData> reservations = event.lines().stream().map(line -> {
            StockReservation reservation = new StockReservation();
            reservation.setOrderId(orderId);
            reservation.setProductId(line.productId());
            reservation.setQuantity(line.quantity());
            reservation.setStatus("HELD");
            reservation.setCreatedAt(now);
            reservationRepository.save(reservation);
            return new ReservationData(line.productId(), line.quantity());
        }).toList();

        StockReservedEvent reserved = new StockReservedEvent(
                orderId, reservations, now, event.correlationId());
        saveToOutbox(orderId, "StockReserved", reserved, event.correlationId());
        log.info("[INVENTORY] Stock reservado para order {} ({} líneas)", orderId, reservations.size());
    }

    @Transactional
    public void release(String orderId, String correlationId) {
        List<StockReservation> reservations = reservationRepository.findByOrderId(orderId);
        LocalDateTime now = LocalDateTime.now();
        for (StockReservation reservation : reservations) {
            if (!"RELEASED".equals(reservation.getStatus())) {
                reservation.setStatus("RELEASED");
                reservation.setReleasedAt(now);
                reservationRepository.save(reservation);
            }
        }
        log.info("[INVENTORY] Reservas liberadas para order {} ({} líneas)", orderId, reservations.size());
    }

    private void saveToOutbox(String aggregateId, String eventType, Object event, String correlationId) {
        OutboxEvent outbox = new OutboxEvent();
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setTopic(KafkaTopics.INVENTORY_EVENTS);
        outbox.setPayloadJson(objectMapper.writeValueAsString(event));
        outbox.setCorrelationId(correlationId);
        outbox.setCreatedAt(LocalDateTime.now());
        outboxRepository.save(outbox);
    }
}
