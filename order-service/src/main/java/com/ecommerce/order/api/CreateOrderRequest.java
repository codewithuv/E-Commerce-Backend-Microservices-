package com.ecommerce.order.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID customerId,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotEmpty @Valid List<Line> lines
) {
    public record Line(
            @NotNull UUID productId,
            @NotBlank String sku,
            @Min(1) @Max(100) int quantity,
            @NotNull @DecimalMin("0.01") BigDecimal unitPrice
    ) {}
}
