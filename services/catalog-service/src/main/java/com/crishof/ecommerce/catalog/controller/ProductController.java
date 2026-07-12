package com.crishof.ecommerce.catalog.controller;

import com.crishof.ecommerce.catalog.domain.Product;
import com.crishof.ecommerce.catalog.service.ProductService;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/catalog/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> request,
            UriComponentsBuilder uriBuilder) {
        Product created = productService.create(request);
        URI location = uriBuilder.path("/api/catalog/products/{id}")
                .buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(toMap(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> findById(@PathVariable Long id) {
        return productService.findById(id)
                .map(p -> ResponseEntity.ok(toMap(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Endpoint batch: order-service pregunta por múltiples productos en una
     * sola llamada y cachea (product_name, unit_price) como snapshot en el pedido.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> findByIds(@RequestParam List<Long> ids) {
        return ResponseEntity.ok(productService.findByIds(ids).stream()
                .map(this::toMap).toList());
    }

    private Map<String, Object> toMap(Product p) {
        return Map.of(
                "id", p.getId(),
                "sku", p.getSku(),
                "name", p.getName(),
                "price", p.getPrice(),
                "stock", p.getStock()
        );
    }
}
