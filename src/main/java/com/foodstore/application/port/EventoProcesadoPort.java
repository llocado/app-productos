package com.foodstore.application.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Soporte de idempotencia para todo consumidor de eventos de este servicio
 * (ver "Contrato de eventos" en el ROADMAP: RabbitMQ entrega at-least-once,
 * cada consumidor debe descartar un eventId ya procesado).
 */
public interface EventoProcesadoPort {

    boolean yaFueProcesado(UUID eventId);

    void marcarComoProcesado(UUID eventId);

    /**
     * Retencion: borra hasta {@code lote} registros anotados antes de {@code limite} y devuelve cuantos borro. Una
     * transaccion por llamada, para no mantener bloqueos largos con tablas grandes.
     */
    int eliminarProcesadosAnterioresA(Instant limite, int lote);
}
