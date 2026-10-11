package com.foodstore.infrastructure.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Unidades que llegan a la bodega. Tope por peticion para evitar un typo que deje el stock absurdo. */
public record StockIngresoRequest(
        @Min(value = 1, message = "Las unidades deben ser al menos 1")
        @Max(value = 100000, message = "Las unidades no pueden superar 100000 por ingreso")
        int unidades
) {
}
