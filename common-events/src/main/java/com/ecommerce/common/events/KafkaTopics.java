package com.ecommerce.common.events;

/**
 * Central registry of Kafka topics used across the platform.
 * Naming convention: <domain>.<event-type>.v<schema-version>
 */
public final class KafkaTopics {
    public static final String ORDER_CREATED          = "orders.order-created.v1";
    public static final String ORDER_COMPLETED        = "orders.order-completed.v1";
    public static final String ORDER_CANCELLED        = "orders.order-cancelled.v1";

    public static final String INVENTORY_RESERVED     = "inventory.stock-reserved.v1";
    public static final String INVENTORY_REJECTED     = "inventory.stock-rejected.v1";
    public static final String INVENTORY_RELEASED     = "inventory.stock-released.v1";

    public static final String PAYMENT_PROCESSED      = "payments.payment-processed.v1";
    public static final String PAYMENT_FAILED         = "payments.payment-failed.v1";

    /** Dead-letter topic suffix appended by the error handler. */
    public static final String DLT_SUFFIX = ".DLT";

    private KafkaTopics() {}
}
