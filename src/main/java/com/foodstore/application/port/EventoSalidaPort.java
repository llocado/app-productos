package com.foodstore.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Registra un evento de dominio para publicarlo despues (patron outbox): se guarda en la misma transaccion que el
 * cambio y un publicador lo envia a RabbitMQ con confirmacion. La capa de aplicacion no conoce RabbitMQ ni el
 * formato del mensaje.
 */
public interface EventoSalidaPort {

    /** Un producto que no alcanzo: lo que pedia el pago y lo que habia realmente. */
    record Faltante(UUID productoId, int solicitado, int disponible) {
    }

    /** El pago aprobado no pudo descontar el stock (no se desconto nada). pedidos-service marca el pedido SIN_STOCK. */
    record StockDescuentoRechazado(UUID pagoId, UUID pedidoId, String usuarioId, List<Faltante> faltantes) {
    }

    void registrar(StockDescuentoRechazado evento, UUID correlationId);

    /**
     * Retencion: borra hasta {@code lote} eventos YA publicados antes de {@code limite} y devuelve cuantos borro. Nunca
     * toca los pendientes (publicado_en nulo): un evento sin publicar no se descarta por antiguo que sea.
     */
    int eliminarPublicadosAnterioresA(Instant limite, int lote);
}
