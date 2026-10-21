package com.foodstore.infrastructure.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductoRequest(
        // Mismo patron que el dominio (Sku): sin esto, un SKU invalido llegaba al dominio y salia como 500.
        @NotBlank(message = "El sku es obligatorio")
        @Pattern(regexp = "^\\s*[A-Za-z0-9][A-Za-z0-9\\-]{2,19}\\s*$",
                message = "El sku debe tener entre 3 y 20 caracteres (letras, digitos o guiones) y comenzar con letra o digito")
        String sku,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
        String nombre,

        String descripcion,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor que 0")
        BigDecimal precioMonto,

        @NotBlank(message = "La moneda es obligatoria")
        @Size(min = 3, max = 3, message = "La moneda debe ser un codigo ISO 4217 de 3 letras (ej. CLP)")
        String moneda,

        @NotBlank(message = "La categoria es obligatoria")
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "La categoria debe ser un UUID valido")
        String categoriaId
) {
}
