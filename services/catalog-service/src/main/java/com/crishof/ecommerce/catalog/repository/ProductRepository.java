package com.crishof.ecommerce.catalog.repository;

import com.crishof.ecommerce.catalog.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);
}
