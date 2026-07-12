package com.crishof.ecommerce.inventory.repository;

import com.crishof.ecommerce.inventory.domain.StockReservation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    List<StockReservation> findByOrderId(String orderId);

    boolean existsByOrderId(String orderId);
}
