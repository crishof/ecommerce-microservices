package com.crishof.ecommerce.order.outbox;

import com.crishof.ecommerce.shared.kafka.KafkaTopics;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Job periódico que publica los eventos de outbox_events en Kafka (cada 500ms).
 * En producción real: reintentos con backoff, dead-letter, particionado, etc.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final String SOURCE_SERVICE = "order-service";

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending =
                outboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (OutboxEvent event : pending) {
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        event.getTopic(), event.getAggregateId(), event.getPayloadJson());
                record.headers()
                        .add(KafkaTopics.HEADER_EVENT_TYPE,
                                event.getEventType().getBytes(StandardCharsets.UTF_8))
                        .add(KafkaTopics.HEADER_SOURCE_SERVICE,
                                SOURCE_SERVICE.getBytes(StandardCharsets.UTF_8));
                if (event.getCorrelationId() != null) {
                    record.headers().add(KafkaTopics.HEADER_CORRELATION_ID,
                            event.getCorrelationId().getBytes(StandardCharsets.UTF_8));
                }
                kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);

                event.setPublishedAt(LocalDateTime.now());
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Error publicando evento outbox id={}: {}",
                        event.getId(), e.getMessage());
                // Se reintentará en la siguiente iteración
            }
        }
    }
}
