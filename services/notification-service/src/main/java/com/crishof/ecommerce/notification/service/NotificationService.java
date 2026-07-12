package com.crishof.ecommerce.notification.service;

import com.crishof.ecommerce.notification.domain.Notification;
import com.crishof.ecommerce.notification.repository.NotificationRepository;
import com.crishof.ecommerce.shared.event.OrderCancelledEvent;
import com.crishof.ecommerce.shared.event.OrderConfirmedEvent;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra notificaciones (simuladas) al confirmarse o cancelarse un pedido.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        Notification notification = new Notification();
        notification.setOrderId(event.aggregateId());
        notification.setCustomerId(event.customerId());
        notification.setCustomerEmail(event.customerEmail());
        notification.setType("ORDER_CONFIRMED");
        notification.setSubject("Tu pedido " + event.aggregateId() + " está confirmado");
        notification.setBody("Hemos cobrado " + event.totalAmount()
                + " y tu pedido está en preparación. ¡Gracias por tu compra!");
        notification.setSentAt(LocalDateTime.now());
        notificationRepository.save(notification);
        log.info("[NOTIFICATION] Confirmación enviada a {} (order {})",
                event.customerEmail(), event.aggregateId());
    }

    @Transactional
    public void onOrderCancelled(OrderCancelledEvent event) {
        Notification notification = new Notification();
        notification.setOrderId(event.aggregateId());
        notification.setCustomerId(event.customerId());
        notification.setCustomerEmail(event.customerEmail());
        notification.setType("ORDER_CANCELLED");
        notification.setSubject("Tu pedido " + event.aggregateId() + " ha sido cancelado");
        notification.setBody("Lo sentimos, tu pedido no pudo completarse. Motivo: " + event.reason());
        notification.setSentAt(LocalDateTime.now());
        notificationRepository.save(notification);
        log.warn("[NOTIFICATION] Cancelación enviada a {} (order {}): {}",
                event.customerEmail(), event.aggregateId(), event.reason());
    }
}
