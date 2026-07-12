package com.foodstore.application.usecase;

import java.math.BigDecimal;

/**
 * Entrada del caso de uso, ya en tipos primitivos: el mapeo a Value Objects del
 * dominio (Sku, Precio, CategoriaId) ocurre dentro de CrearProductoUseCase,
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
