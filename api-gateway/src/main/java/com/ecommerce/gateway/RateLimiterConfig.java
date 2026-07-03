package com.ecommerce.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    /**
     * Rate-limit key: authenticated user id if present, otherwise client IP.
     * Backed by Redis so limits hold across gateway replicas.
     */
    @Bean
    public KeyResolver principalOrIpKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(java.security.Principal::getName)
                .switchIfEmpty(Mono.justOrEmpty(
                        exchange.getRequest().getRemoteAddress() != null
                                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                                : "anonymous"));
    }
}
