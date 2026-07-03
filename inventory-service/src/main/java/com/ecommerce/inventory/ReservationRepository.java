package com.ecommerce.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    List<Reservation> findByOrderIdAndStatus(UUID orderId, Reservation.Status status);
}
