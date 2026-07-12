package com.crishof.ecommerce.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades del gateway de pagos simulado (app.payments.*).
 * Record con un único constructor canónico (binding de Spring Boot).
 */
@ConfigurationProperties(prefix = "app.payments")
public record PaymentsProperties(
        double failureRate,
        long processingDelayMs
) {
}
