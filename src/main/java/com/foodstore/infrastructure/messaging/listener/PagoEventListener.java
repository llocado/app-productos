package com.foodstore.infrastructure.messaging.listener;

import com.foodstore.application.usecase.DescontarStockPorPagoUseCase;
import com.foodstore.application.usecase.DescontarStockPorPagoUseCase.Comando;
import com.foodstore.application.usecase.DescontarStockPorPagoUseCase.ItemComprado;
import com.foodstore.infrastructure.messaging.config.RabbitMQConfig;
import com.foodstore.infrastructure.messaging.dto.EventEnvelope;
import com.foodstore.infrastructure.messaging.dto.PagoAprobadoPayload;
import com.foodstore.infrastructure.messaging.dto.PagoAprobadoPayload.ItemDto;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Consume pago.aprobado de pagos-service para descontar el stock. Politica de errores (ver
 * spring.rabbitmq.listener.simple en application.yml):
 * - Mensaje irrecuperable (payload invalido): DLQ de inmediato, sin reintentos.
 * - Fallo transitorio (la base no responde, conflicto de concurrencia por otro descuento): se deja propagar; Spring
 *   reintenta 1 + 3 veces con espera creciente y luego lo manda a la DLQ. Nunca se reencola en bucle.
 * - Choque de unicidad por un duplicado concurrente: si ya estaba procesado, se descarta.
 * Que falte stock NO es un error del mensaje: el caso de uso lo resuelve publicando stock.descuento-rechazado.
 * Los logs llevan eventId y correlationId, nunca el payload completo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PagoEventListener {

    static final int MAX_ITEMS = 500;
    static final int MAX_USUARIO_ID = 100;
    /** Tope por item: con 500 items la suma cabe en un int y un valor absurdo se rechaza como payload invalido. */
    static final int MAX_CANTIDAD = 1_000_000;

    private final DescontarStockPorPagoUseCase descontarStockPorPagoUseCase;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PAGO_APROBADO)
    public void escucharPagoAprobado(EventEnvelope envelope) {
        if (envelope == null || envelope.eventId() == null || envelope.payload() == null) {
            log.warn("Evento descartado a la DLQ: falta eventId o payload");
            throw new AmqpRejectAndDontRequeueException("Envelope invalido: falta eventId o payload");
        }
        UUID correlacion = envelope.correlationId() != null ? envelope.correlationId() : envelope.eventId();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", correlacion.toString())) {
            log.info("Evento recibido eventType={} eventId={}", envelope.eventType(), envelope.eventId());

            PagoAprobadoPayload payload = leerPayload(envelope);
            String motivo = validar(payload);
            if (motivo != null) {
                log.warn("Evento descartado a la DLQ eventId={}: payload invalido ({})", envelope.eventId(), motivo);
                throw new AmqpRejectAndDontRequeueException("Payload invalido: " + motivo);
            }
            procesar(envelope, payload, correlacion);
        }
    }

    private void procesar(EventEnvelope envelope, PagoAprobadoPayload payload, UUID correlacion) {
        List<ItemComprado> items = payload.items().stream().map(i -> new ItemComprado(i.productoId(), i.cantidad())).toList();
        Comando comando = new Comando(envelope.eventId(), correlacion, payload.pagoId(), payload.pedidoId(), payload.usuarioId(), items);
        try {
            descontarStockPorPagoUseCase.execute(comando);
        } catch (DataIntegrityViolationException e) {
            if (descontarStockPorPagoUseCase.yaProcesado(envelope.eventId())) {
                log.info("Duplicado concurrente descartado eventId={}", envelope.eventId());
                return;
            }
            log.warn("Fallo de integridad al procesar evento eventId={}: {}", envelope.eventId(), e.toString());
            throw e;
        } catch (RuntimeException e) {
            log.warn("Fallo al procesar evento eventId={}: {}", envelope.eventId(), e.toString());
            throw e;
        }
    }

    private PagoAprobadoPayload leerPayload(EventEnvelope envelope) {
        try {
            return objectMapper.treeToValue(envelope.payload(), PagoAprobadoPayload.class);
        } catch (JacksonException e) {
            log.warn("Evento descartado a la DLQ eventId={}: payload no deserializable", envelope.eventId());
            throw new AmqpRejectAndDontRequeueException("Payload no deserializable", e);
        }
    }

    /** Retorna el motivo si el payload es invalido, o null si esta bien. */
    static String validar(PagoAprobadoPayload payload) {
        if (payload == null) return "payload nulo";
        if (payload.pagoId() == null) return "falta pagoId";
        if (payload.pedidoId() == null) return "falta pedidoId";
        if (payload.usuarioId() == null || payload.usuarioId().isBlank() || payload.usuarioId().length() > MAX_USUARIO_ID) {
            return "usuarioId ausente o demasiado largo";
        }
        return validarItems(payload.items());
    }

    private static String validarItems(List<ItemDto> items) {
        if (items == null || items.isEmpty()) return "sin items";
        if (items.size() > MAX_ITEMS) return "demasiados items";
        for (ItemDto item : items) {
            if (item == null) return "item nulo";
            if (item.productoId() == null) return "item sin productoId";
            if (item.cantidad() <= 0 || item.cantidad() > MAX_CANTIDAD) return "item con cantidad invalida";
        }
        return null;
    }
}
