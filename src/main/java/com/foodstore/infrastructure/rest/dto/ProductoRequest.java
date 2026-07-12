package com.foodstore.infrastructure.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductoRequest(
        @NotBlank(message = "El sku es obligatorio")
        String sku,

        @NotBlank(message = "El nombre es obligatorio")
        String nombre,

        String descripcion,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor que 0")
        BigDecimal precioMonto,

        @NotBlank(message = "La moneda es obligatoria")
        @Size(min = 3, max = 3, message = "La moneda debe ser un codigo ISO 4217 de 3 letras (ej. CLP)")
        String moneda,

        @NotBlank(message = "La categoria es obligatoria")
        String categoriaId
) {
}
