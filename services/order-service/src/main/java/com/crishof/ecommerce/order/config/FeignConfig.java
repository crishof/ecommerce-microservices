package com.crishof.ecommerce.order.config;

import com.crishof.ecommerce.shared.kafka.KafkaTopics;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Propaga el correlation-id del MDC en las llamadas REST salientes de Feign
 * (identity-service, catalog-service), manteniendo la traza end-to-end.
 */
@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor correlationIdForwarder() {
        return template -> {
            String correlationId = MDC.get("correlationId");
            if (correlationId != null && !correlationId.isBlank()) {
                template.header(KafkaTopics.HEADER_CORRELATION_ID, correlationId);
            }
        };
    }
}
