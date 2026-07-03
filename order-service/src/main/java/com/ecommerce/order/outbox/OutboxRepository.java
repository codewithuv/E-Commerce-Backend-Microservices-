package com.ecommerce.order.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * PESSIMISTIC_WRITE + SKIP LOCKED semantics let multiple relay instances
     * run concurrently without double-publishing the same row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT o FROM OutboxEvent o WHERE o.publishedAt IS NULL AND o.attempts < 10 ORDER BY o.createdAt ASC LIMIT 50")
    List<OutboxEvent> findBatchToPublish();
}
