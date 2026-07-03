package com.ecommerce.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Circuit-breaker fallbacks. When a downstream service trips the breaker,
 * the gateway degrades gracefully instead of cascading the failure.
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/orders")
    public Mono<ResponseEntity<Map<String, String>>> ordersFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", "DEGRADED",
                        "message", "Order service is temporarily unavailable. Your request was not processed — please retry shortly."
                )));
    }

    @RequestMapping("/fallback/inventory")
    public Mono<ResponseEntity<Map<String, String>>> inventoryFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "DEGRADED", "message", "Inventory service is temporarily unavailable.")));
    }
}
