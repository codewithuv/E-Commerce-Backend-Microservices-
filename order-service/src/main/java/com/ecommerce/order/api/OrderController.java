package com.ecommerce.order.api;

import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderApplicationService service;
    private final OrderRepository repository;

    public OrderController(OrderApplicationService service, OrderRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    /**
     * Accepts the order and kicks off the saga. Returns 202 because
     * fulfilment is asynchronous — clients poll GET /{id} or subscribe
     * to notifications for the terminal state.
     *
     * Idempotency-Key header lets clients safely retry POSTs.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {

        Order order = service.placeOrder(request, idempotencyKey);
        return ResponseEntity
                .accepted()
                .location(URI.create("/api/orders/" + order.getId()))
                .body(Map.of("orderId", order.getId(), "status", order.getStatus()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> get(@PathVariable UUID id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
