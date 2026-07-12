package com.crishof.ecommerce.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crishof.ecommerce.inventory.domain.StockReservation;
import com.crishof.ecommerce.inventory.outbox.OutboxEvent;
import com.crishof.ecommerce.inventory.outbox.OutboxRepository;
import com.crishof.ecommerce.inventory.repository.StockReservationRepository;
import com.crishof.ecommerce.inventory.service.InventoryService;
import com.crishof.ecommerce.shared.event.OrderPlacedEvent;
import com.crishof.ecommerce.shared.event.OrderPlacedEvent.OrderLineData;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    StockReservationRepository reservationRepository;
    @Mock
    OutboxRepository outboxRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void reserveCreatesReservationsAndOutboxEvent() {
        InventoryService service =
                new InventoryService(reservationRepository, outboxRepository, objectMapper);
        when(reservationRepository.existsByOrderId("o1")).thenReturn(false);

        OrderPlacedEvent event = new OrderPlacedEvent(
                "o1", 2L, "alice@shop.com", "Alice",
                List.of(new OrderLineData("10", "Laptop", new BigDecimal("1299.99"), 2)),
                new BigDecimal("2599.98"), LocalDateTime.now(), "corr-1");

        service.reserve(event);

        verify(reservationRepository, times(1)).save(org.mockito.ArgumentMatchers.any(StockReservation.class));
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("StockReserved");
        assertThat(captor.getValue().getTopic()).isEqualTo("inventory-events");
    }

    @Test
    void reserveSkipsDuplicateOrder() {
        InventoryService service =
                new InventoryService(reservationRepository, outboxRepository, objectMapper);
        when(reservationRepository.existsByOrderId("o1")).thenReturn(true);

        OrderPlacedEvent event = new OrderPlacedEvent(
                "o1", 2L, "alice@shop.com", "Alice",
                List.of(new OrderLineData("10", "Laptop", new BigDecimal("1299.99"), 1)),
                new BigDecimal("1299.99"), LocalDateTime.now(), "corr-1");

        service.reserve(event);

        verify(outboxRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }
}
