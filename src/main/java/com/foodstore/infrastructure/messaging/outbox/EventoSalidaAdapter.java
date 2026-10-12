package com.foodstore.infrastructure.messaging.outbox;

import com.foodstore.application.port.EventoSalidaPort;
import com.foodstore.infrastructure.messaging.config.RabbitMQConfig;
import com.foodstore.infrastructure.messaging.dto.EventEnvelope;
import com.foodstore.infrastructure.messaging.dto.StockDescuentoRechazadoPayload;
import com.foodstore.infrastructure.persistence.entity.EventoSalidaEntity;
import com.foodstore.infrastructure.persistence.repository.SpringDataEventoSalidaRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Guarda el evento (envelope completo en JSON) en eventos_salida, dentro de la transaccion del caso de uso que
 * llama. El eventId es determinista (tipo + pagoId): registrar dos veces el mismo evento falla por la clave
 * primaria en vez de duplicarlo ni sobrescribir la fila (EventoSalidaEntity es Persistable para que sea persist, no merge).
 */
@Component
@RequiredArgsConstructor
public class EventoSalidaAdapter implements EventoSalidaPort {

    private static final String PRODUCER = "app-productos";

    private final SpringDataEventoSalidaRepository springDataEventoSalidaRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void registrar(StockDescuentoRechazado evento, UUID correlationId) {
        StockDescuentoRechazadoPayload payload = new StockDescuentoRechazadoPayload(
                evento.pagoId(), evento.pedidoId(), evento.usuarioId(),
                evento.faltantes().stream()
                        .map(f -> new StockDescuentoRechazadoPayload.FaltanteDto(f.productoId(), f.solicitado(), f.disponible()))
                        .toList());
        String tipo = RabbitMQConfig.ROUTING_KEY_STOCK_DESCUENTO_RECHAZADO;
        UUID eventId = UUID.nameUUIDFromBytes((tipo + ":" + evento.pagoId()).getBytes(StandardCharsets.UTF_8));
        Instant ahora = Instant.now();
        EventEnvelope envelope = new EventEnvelope(eventId, tipo, 1, ahora, PRODUCER, correlationId,
                objectMapper.valueToTree(payload));

        EventoSalidaEntity entity = new EventoSalidaEntity();
        entity.setId(eventId);
        entity.setEventType(tipo);
        entity.setRoutingKey(tipo);
        entity.setCorrelationId(correlationId);
        entity.setPayload(objectMapper.writeValueAsString(envelope));
        entity.setCreadoEn(ahora);
        entity.setIntentos(0);
        springDataEventoSalidaRepository.saveAndFlush(entity);
    }
}
