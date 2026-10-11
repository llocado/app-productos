package com.foodstore.infrastructure.messaging.dto;

import java.util.List;
import java.util.UUID;

/**
 * DTO de deserializacion del payload de pago.aprobado, armado a partir del JSON Schema que publica pagos-service
 * (docs/events/pago.aprobado.schema.json). Solo los campos que productos necesita; los demas se ignoran
 * (evolucion aditiva del contrato).
 */
public record PagoAprobadoPayload(UUID pagoId, UUID pedidoId, String usuarioId, List<ItemDto> items) {

    public record ItemDto(UUID productoId, int cantidad) {
    }
}
