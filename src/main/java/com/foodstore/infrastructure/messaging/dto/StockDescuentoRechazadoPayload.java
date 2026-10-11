package com.foodstore.infrastructure.messaging.dto;

import java.util.List;
import java.util.UUID;

/** Payload de stock.descuento-rechazado; contrato en docs/events/stock.descuento-rechazado.schema.json. */
public record StockDescuentoRechazadoPayload(
        UUID pagoId,
        UUID pedidoId,
        String usuarioId,
        List<FaltanteDto> faltantes
) {
    public record FaltanteDto(UUID productoId, int solicitado, int disponible) {
    }
}
