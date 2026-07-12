package com.crishof.ecommerce.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Extrae x-correlation-id del request y lo pone en el MDC de Logback para
 * trazabilidad end-to-end. Si no viene, genera uno nuevo y lo devuelve en la
 * respuesta.
 */
public class CorrelationIdInterceptor implements HandlerInterceptor {

    public static final String MDC_KEY = "correlationId";
    private static final String HEADER = "x-correlation-id";

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        String correlationId = req.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        res.setHeader(HEADER, correlationId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res,
                                Object handler, Exception ex) {
        MDC.remove(MDC_KEY);
    }
}
