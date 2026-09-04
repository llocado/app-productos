package com.foodstore.infrastructure.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record GenerarDescripcionRequest(
        @NotBlank(message = "El nombre es obligatorio")
        String nombre,

        String categoria
) {
}
