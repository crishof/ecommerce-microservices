package com.crishof.ecommerce.payment.service;

import com.crishof.ecommerce.payment.config.PaymentsProperties;
import com.crishof.ecommerce.payment.domain.Payment;
import com.crishof.ecommerce.payment.outbox.OutboxEvent;
import com.crishof.ecommerce.payment.outbox.OutboxRepository;
import com.crishof.ecommerce.payment.repository.PaymentRepository;
import com.crishof.ecommerce.shared.event.OrderPlacedEvent;
import com.crishof.ecommerce.shared.event.PaymentFailedEvent;
import com.crishof.ecommerce.shared.event.PaymentSucceededEvent;
import com.crishof.ecommerce.shared.event.StockReservedEvent;
import com.crishof.ecommerce.shared.kafka.KafkaTopics;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Gateway de pagos simulado.
 *
 * StockReservedEvent no transporta importe ni customerId; por eso payment-service
 * mantiene una proyección local (tabla payments en estado PENDING) alimentada por
 * OrderPlacedEvent. Cuando llega StockReserved, cobra usando esa proyección.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final PaymentsProperties properties;

    public PaymentService(PaymentRepository paymentRepository,
                          OutboxRepository outboxRepository,
                          ObjectMapper objectMapper,
                          PaymentsProperties properties) {
        this.paymentRepository = paymentRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** Proyección: registra el cobro pendiente al conocerse el pedido. */
    @Transactional
    public void registerPending(OrderPlacedEvent event) {
        if (paymentRepository.findByOrderId(event.aggregateId()).isPresent()) {
            return;
        }
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID().toString());
        payment.setOrderId(event.aggregateId());
        payment.setCustomerId(event.customerId());
        payment.setAmount(event.totalAmount());
        payment.setStatus("PENDING");
        payment.setProcessedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        log.info("[PAYMENT] Cobro pendiente registrado para order {} importe {}",
                event.aggregateId(), event.totalAmount());
    }

    /** Intenta cobrar cuando el stock queda reservado. */
    @Transactional
    public void charge(StockReservedEvent event) {
        String orderId = event.aggregateId();
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "No hay cobro pendiente para order " + orderId + " (proyección aún no disponible)"));

        if (!"PENDING".equals(payment.getStatus())) {
            log.info("[PAYMENT] Order {} ya procesada ({}), ignorando duplicado",
                    orderId, payment.getStatus());
            return;
        }

        simulateLatency();
        boolean success = ThreadLocalRandom.current().nextDouble() >= properties.failureRate();
        LocalDateTime now = LocalDateTime.now();
        payment.setProcessedAt(now);

        if (success) {
            String transactionId = "TX-" + UUID.randomUUID();
            payment.setStatus("SUCCEEDED");
            payment.setTransactionId(transactionId);
            paymentRepository.save(payment);

            PaymentSucceededEvent succeeded = new PaymentSucceededEvent(
                    payment.getId(), orderId, payment.getCustomerId(),
                    payment.getAmount(), transactionId, now, event.correlationId());
            saveToOutbox(payment.getId(), "PaymentSucceeded", succeeded, event.correlationId());
            log.info("[PAYMENT] Cobro OK order {} tx {}", orderId, transactionId);
        } else {
            String reason = "Fondos insuficientes / rechazo del emisor";
            payment.setStatus("FAILED");
            payment.setFailureReason(reason);
            paymentRepository.save(payment);

            PaymentFailedEvent failed = new PaymentFailedEvent(
                    payment.getId(), orderId, payment.getCustomerId(),
                    payment.getAmount(), reason, now, event.correlationId());
            saveToOutbox(payment.getId(), "PaymentFailed", failed, event.correlationId());
            log.warn("[PAYMENT] Cobro RECHAZADO order {}: {}", orderId, reason);
        }
    }

    private void simulateLatency() {
        long delay = properties.processingDelayMs();
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void saveToOutbox(String aggregateId, String eventType, Object event, String correlationId) {
        OutboxEvent outbox = new OutboxEvent();
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setTopic(KafkaTopics.PAYMENT_EVENTS);
        outbox.setPayloadJson(objectMapper.writeValueAsString(event));
        outbox.setCorrelationId(correlationId);
        outbox.setCreatedAt(LocalDateTime.now());
        outboxRepository.save(outbox);
    }
}
