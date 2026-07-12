package com.crishof.ecommerce.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.crishof.ecommerce.notification.domain.Notification;
import com.crishof.ecommerce.notification.repository.NotificationRepository;
import com.crishof.ecommerce.notification.service.NotificationService;
import com.crishof.ecommerce.shared.event.OrderCancelledEvent;
import com.crishof.ecommerce.shared.event.OrderConfirmedEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    NotificationRepository notificationRepository;

    @Test
    void confirmedStoresSuccessNotification() {
        NotificationService service = new NotificationService(notificationRepository);
        service.onOrderConfirmed(new OrderConfirmedEvent(
                "o1", 2L, "alice@shop.com", new BigDecimal("99.99"),
                LocalDateTime.now(), "corr-1"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        Mockito.verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("ORDER_CONFIRMED");
        assertThat(captor.getValue().getCustomerEmail()).isEqualTo("alice@shop.com");
    }

    @Test
    void cancelledStoresFailureNotification() {
        NotificationService service = new NotificationService(notificationRepository);
        service.onOrderCancelled(new OrderCancelledEvent(
                "o1", 2L, "alice@shop.com", "Pago rechazado",
                LocalDateTime.now(), "corr-1"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        Mockito.verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("ORDER_CANCELLED");
        assertThat(captor.getValue().getBody()).contains("Pago rechazado");
    }
}
