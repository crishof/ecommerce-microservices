package com.crishof.ecommerce.notification.repository;

import com.crishof.ecommerce.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
