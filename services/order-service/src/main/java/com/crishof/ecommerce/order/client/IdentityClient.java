package com.crishof.ecommerce.order.client;

import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Cliente REST declarativo. Feign resuelve "identity-service" vía Eureka + LoadBalancer.
 * Un 404 se traduce en FeignException.NotFound (se maneja en OrderService).
 */
@FeignClient(name = "identity-service", path = "/api/users")
public interface IdentityClient {

    @GetMapping("/{id}")
    Map<String, Object> findById(@PathVariable Long id);
}
