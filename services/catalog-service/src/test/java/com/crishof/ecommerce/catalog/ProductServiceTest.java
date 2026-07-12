package com.crishof.ecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.crishof.ecommerce.catalog.domain.Product;
import com.crishof.ecommerce.catalog.repository.ProductRepository;
import com.crishof.ecommerce.catalog.service.ProductService;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    ProductRepository productRepository;

    @InjectMocks
    ProductService productService;

    @Test
    void createsProductFromMap() {
        when(productRepository.existsBySku("SKU-1")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product created = productService.create(Map.of(
                "sku", "SKU-1", "name", "Test", "price", 10.5, "stock", 3));

        assertThat(created.getSku()).isEqualTo("SKU-1");
        assertThat(created.getPrice()).isEqualByComparingTo(new BigDecimal("10.5"));
        assertThat(created.getStock()).isEqualTo(3);
    }

    @Test
    void rejectsDuplicateSku() {
        when(productRepository.existsBySku("SKU-DUP")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(Map.of("sku", "SKU-DUP", "name", "x", "price", 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
