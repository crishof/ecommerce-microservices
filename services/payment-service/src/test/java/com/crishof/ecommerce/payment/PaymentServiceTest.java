package com.crishof.ecommerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.crishof.ecommerce.payment.config.PaymentsProperties;
import com.crishof.ecommerce.payment.domain.Payment;
import com.crishof.ecommerce.payment.outbox.OutboxEvent;
import com.crishof.ecommerce.payment.outbox.OutboxRepository;
import com.crishof.ecommerce.payment.repository.PaymentRepository;
import com.crishof.ecommerce.payment.service.PaymentService;
import com.crishof.ecommerce.shared.event.StockReservedEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    OutboxRepository outboxRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private Payment pending() {
        Payment p = new Payment();
        p.setId("pay-1");
        p.setOrderId("o1");
        p.setCustomerId(2L);
        p.setAmount(new BigDecimal("99.99"));
        p.setStatus("PENDING");
        p.setProcessedAt(LocalDateTime.now());
        return p;
    }

    private StockReservedEvent reservedEvent() {
        return new StockReservedEvent("o1",
                List.of(new StockReservedEvent.ReservationData("10", 1)),
                LocalDateTime.now(), "corr-1");
    }

    @Test
    void chargeAlwaysSucceedsWithZeroFailureRate() {
        PaymentService service = new PaymentService(paymentRepository, outboxRepository,
                objectMapper, new PaymentsProperties(0.0, 0));
        when(paymentRepository.findByOrderId("o1")).thenReturn(Optional.of(pending()));

        service.charge(reservedEvent());

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        org.mockito.Mockito.verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("PaymentSucceeded");
        assertThat(captor.getValue().getTopic()).isEqualTo("payment-events");
    }

    @Test
    void chargeAlwaysFailsWithFullFailureRate() {
        PaymentService service = new PaymentService(paymentRepository, outboxRepository,
                objectMapper, new PaymentsProperties(1.0, 0));
        when(paymentRepository.findByOrderId("o1")).thenReturn(Optional.of(pending()));

        service.charge(reservedEvent());

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        org.mockito.Mockito.verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("PaymentFailed");
    }

    @Test
    void chargeSkipsAlreadyProcessed() {
        PaymentService service = new PaymentService(paymentRepository, outboxRepository,
                objectMapper, new PaymentsProperties(0.0, 0));
        Payment done = pending();
        done.setStatus("SUCCEEDED");
        when(paymentRepository.findByOrderId("o1")).thenReturn(Optional.of(done));

        service.charge(reservedEvent());

        org.mockito.Mockito.verify(outboxRepository, org.mockito.Mockito.never()).save(any());
    }
}
