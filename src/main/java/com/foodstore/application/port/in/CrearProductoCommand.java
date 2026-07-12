package com.foodstore.application.port.in;

import java.math.BigDecimal;

/**
 * Entrada del caso de uso, ya en tipos primitivos: el mapeo a Value Objects del
 * dominio (Sku, Precio, CategoriaId) ocurre dentro del servicio de aplicacion,
 * no en el controller.
 */
public record CrearProductoCommand(
        String sku,
        String nombre,
        String descripcion,
        BigDecimal precioMonto,
        String moneda,
        String categoriaId
) {
}
