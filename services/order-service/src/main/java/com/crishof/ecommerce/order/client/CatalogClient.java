package com.crishof.ecommerce.order.client;

import java.util.List;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Cliente REST declarativo hacia catalog-service (batch de productos).
 */
@FeignClient(name = "catalog-service", path = "/api/catalog/products")
public interface CatalogClient {

    @GetMapping
    List<Map<String, Object>> findByIds(@RequestParam("ids") List<Long> ids);
}
