package com.crishof.ecommerce.catalog.service;

import com.crishof.ecommerce.catalog.domain.Product;
import com.crishof.ecommerce.catalog.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public Product create(Map<String, Object> request) {
        String sku = Objects.toString(request.get("sku"), null);
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku es obligatorio");
        }
        if (productRepository.existsBySku(sku)) {
            throw new IllegalArgumentException("Ya existe un producto con sku " + sku);
        }

        Product product = new Product();
        product.setSku(sku);
        product.setName(Objects.toString(request.get("name"), null));
        product.setDescription(Objects.toString(request.get("description"), null));
        product.setPrice(new BigDecimal(Objects.toString(request.get("price"), "0")));
        product.setStock(request.get("stock") == null
                ? 0 : ((Number) request.get("stock")).intValue());
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Product> findByIds(List<Long> ids) {
        return productRepository.findAllById(ids);
    }
}
