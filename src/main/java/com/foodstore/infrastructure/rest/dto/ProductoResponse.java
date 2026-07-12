package com.foodstore.infrastructure.rest.dto;

import java.math.BigDecimal;

public record ProductoResponse(
        String id,
        String sku,
        String nombre,
        String descripcion,
        BigDecimal precioMonto,
        String moneda,
        int stock,
        String categoriaId,
        boolean activo
) {
}
