package com.crishof.ecommerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crishof.ecommerce.order.client.CatalogClient;
import com.crishof.ecommerce.order.client.IdentityClient;
import com.crishof.ecommerce.order.domain.Order;
import com.crishof.ecommerce.order.outbox.OutboxEvent;
import com.crishof.ecommerce.order.outbox.OutboxRepository;
import com.crishof.ecommerce.order.repository.OrderRepository;
import com.crishof.ecommerce.order.service.OrderService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    OrderRepository orderRepository;
    @Mock
    OutboxRepository outboxRepository;
    @Mock
    IdentityClient identityClient;
    @Mock
    CatalogClient catalogClient;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private OrderService service() {
        return new OrderService(orderRepository, outboxRepository,
                identityClient, catalogClient, objectMapper);
    }

    @Test
    void placeOrderPersistsOrderAndOutboxEvent() {
        when(identityClient.findById(2L)).thenReturn(Map.of(
                "id", 2, "email", "alice@shop.com", "name", "Alice Buyer"));
        when(catalogClient.findByIds(anyList())).thenReturn(List.of(Map.of(
                "id", 10, "sku", "SKU-1", "name", "Laptop", "price", "1299.99", "stock", 5)));

        String orderId = service().placeOrder(2L, List.of(Map.of("productId", 10, "quantity", 2)));

        assertThat(orderId).isNotBlank();

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getCustomerEmail()).isEqualTo("alice@shop.com");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("2599.98");
        assertThat(saved.getTotalItems()).isEqualTo(2);
        assertThat(saved.getLinesJson()).contains("Laptop");

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        OutboxEvent outbox = outboxCaptor.getValue();
        assertThat(outbox.getEventType()).isEqualTo("OrderPlaced");
        assertThat(outbox.getTopic()).isEqualTo("order-events");
        // El payload contiene el timestamp serializado por Jackson 3 (java.time)
        assertThat(outbox.getPayloadJson()).contains("occurredAt").contains(orderId);
    }

    @Test
    void placeOrderRejectsEmptyLines() {
        assertThatThrownBy(() -> service().placeOrder(2L, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
