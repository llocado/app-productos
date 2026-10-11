package com.foodstore.infrastructure.messaging.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodstore.application.usecase.DescontarStockPorPagoUseCase;
import com.foodstore.application.usecase.DescontarStockPorPagoUseCase.Comando;
import com.foodstore.application.usecase.DescontarStockPorPagoUseCase.ItemComprado;
import com.foodstore.infrastructure.messaging.dto.EventEnvelope;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PagoEventListenerTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID CORRELACION = UUID.randomUUID();
    private static final UUID PAGO_ID = UUID.randomUUID();
    private static final UUID PEDIDO_ID = UUID.randomUUID();
    private static final UUID PRODUCTO_ID = UUID.randomUUID();
    private static final String ITEM = "{\"productoId\":\"" + PRODUCTO_ID + "\",\"cantidad\":2}";

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Mock
    private DescontarStockPorPagoUseCase descontarStockPorPago;

    private PagoEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new PagoEventListener(descontarStockPorPago, objectMapper);
    }

    @Test
    void deberiaDelegarEnElCasoDeUsoConElComandoCompleto_CuandoElPayloadEsValido() {
        listener.escucharPagoAprobado(envelope(CORRELACION, payload("\"u1\"", "[" + ITEM + "]")));

        ArgumentCaptor<Comando> comando = ArgumentCaptor.forClass(Comando.class);
        verify(descontarStockPorPago).execute(comando.capture());
        assertThat(comando.getValue()).isEqualTo(new Comando(EVENT_ID, CORRELACION, PAGO_ID, PEDIDO_ID, "u1",
                List.of(new ItemComprado(PRODUCTO_ID, 2))));
    }

    @Test
    void deberiaUsarElEventIdComoCorrelacion_CuandoElEventoNoTraeCorrelationId() {
        listener.escucharPagoAprobado(envelope(null, payload("\"u1\"", "[" + ITEM + "]")));

        ArgumentCaptor<Comando> comando = ArgumentCaptor.forClass(Comando.class);
        verify(descontarStockPorPago).execute(comando.capture());
        assertThat(comando.getValue().correlationId()).isEqualTo(EVENT_ID);
    }

    @Test
    void deberiaRechazar_CuandoElPayloadEstaVacioOIncompleto() {
        assertRechazado(objectMapper.readTree("{}"));
        assertRechazado(objectMapper.readTree("{\"pedidoId\":\"" + PEDIDO_ID + "\",\"usuarioId\":\"u1\",\"items\":[" + ITEM + "]}"));
        assertRechazado(objectMapper.readTree("{\"pagoId\":\"" + PAGO_ID + "\",\"usuarioId\":\"u1\",\"items\":[" + ITEM + "]}"));
    }

    @Test
    void deberiaRechazar_CuandoUnCampoTieneElTipoIncorrecto() {
        // Items validos: lo unico invalido es el tipo de pagoId.
        assertRechazado(objectMapper.readTree("{\"pagoId\":\"no-es-uuid\",\"pedidoId\":\"" + PEDIDO_ID + "\",\"usuarioId\":\"u1\",\"items\":[" + ITEM + "]}"));
    }

    @Test
    void deberiaAceptarUsuarioIdDeExactamente100Caracteres_YRechazarUnoDe101() {
        listener.escucharPagoAprobado(envelope(null, payload("\"" + "u".repeat(100) + "\"", "[" + ITEM + "]")));
        verify(descontarStockPorPago).execute(any());
        clearInvocations(descontarStockPorPago);

        assertRechazado(payload("\"" + "u".repeat(101) + "\"", "[" + ITEM + "]"));
        assertRechazado(payload("\"\"", "[" + ITEM + "]"));
        assertRechazado(payload("null", "[" + ITEM + "]"));
    }

    @Test
    void deberiaAceptar500Items_YRechazar501OCeroOUnaListaNula() {
        listener.escucharPagoAprobado(envelope(null, payload("\"u1\"", items(500))));
        verify(descontarStockPorPago).execute(any());
        clearInvocations(descontarStockPorPago);

        assertRechazado(payload("\"u1\"", items(501)));
        assertRechazado(payload("\"u1\"", "[]"));
        assertRechazado(payload("\"u1\"", "null"));
    }

    @Test
    void deberiaValidarLosLimitesDeLaCantidad() {
        listener.escucharPagoAprobado(envelope(null, payload("\"u1\"", itemConCantidad(1))));
        listener.escucharPagoAprobado(envelope(null, payload("\"u1\"", itemConCantidad(1_000_000))));
        clearInvocations(descontarStockPorPago);

        assertRechazado(payload("\"u1\"", itemConCantidad(0)));
        assertRechazado(payload("\"u1\"", itemConCantidad(-1)));
        assertRechazado(payload("\"u1\"", itemConCantidad(1_000_001)));
    }

    @Test
    void deberiaRechazar_CuandoUnItemNoTieneProductoIdOEsNulo() {
        assertRechazado(payload("\"u1\"", "[{\"cantidad\":2}]"));
        assertRechazado(payload("\"u1\"", "[null]"));
    }

    @Test
    void deberiaRechazar_CuandoElEnvelopeEstaIncompleto() {
        EventEnvelope sinId = new EventEnvelope(null, "pago.aprobado", 1, Instant.now(), "pagos-service", null, payload("\"u1\"", "[" + ITEM + "]"));
        EventEnvelope sinPayload = new EventEnvelope(EVENT_ID, "pago.aprobado", 1, Instant.now(), "pagos-service", null, null);

        assertThatThrownBy(() -> listener.escucharPagoAprobado(sinId)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.escucharPagoAprobado(sinPayload)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.escucharPagoAprobado(null)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(descontarStockPorPago, never()).execute(any());
    }

    @Test
    void deberiaPropagarSinEnvolver_UnFalloTransitorioParaQueSeReintente() {
        doThrow(new OptimisticLockingFailureException("otro pago descontaba lo mismo")).when(descontarStockPorPago).execute(any());

        assertThatThrownBy(() -> listener.escucharPagoAprobado(envelope(null, payload("\"u1\"", "[" + ITEM + "]"))))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void deberiaDescartarElDuplicado_CuandoElChoqueDeUnicidadSeDebeAUnEventoYaProcesado() {
        doThrow(new DataIntegrityViolationException("pk_eventos_procesados")).when(descontarStockPorPago).execute(any());
        when(descontarStockPorPago.yaProcesado(EVENT_ID)).thenReturn(true);

        assertThatCode(() -> listener.escucharPagoAprobado(envelope(null, payload("\"u1\"", "[" + ITEM + "]"))))
                .doesNotThrowAnyException();
    }

    @Test
    void deberiaPropagar_CuandoElChoqueDeIntegridadNoEsUnDuplicado() {
        doThrow(new DataIntegrityViolationException("otra restriccion")).when(descontarStockPorPago).execute(any());
        when(descontarStockPorPago.yaProcesado(EVENT_ID)).thenReturn(false);

        assertThatThrownBy(() -> listener.escucharPagoAprobado(envelope(null, payload("\"u1\"", "[" + ITEM + "]"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void assertRechazado(JsonNode payload) {
        assertThatThrownBy(() -> listener.escucharPagoAprobado(envelope(null, payload)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(descontarStockPorPago, never()).execute(any());
    }

    private EventEnvelope envelope(UUID correlationId, JsonNode payload) {
        return new EventEnvelope(EVENT_ID, "pago.aprobado", 1, Instant.now(), "pagos-service", correlationId, payload);
    }

    private JsonNode payload(String usuarioId, String items) {
        return objectMapper.readTree("{\"pagoId\":\"" + PAGO_ID + "\",\"pedidoId\":\"" + PEDIDO_ID + "\",\"usuarioId\":" + usuarioId
                + ",\"items\":" + items + "}");
    }

    private static String items(int n) {
        return "[" + String.join(",", Collections.nCopies(n, ITEM)) + "]";
    }

    private static String itemConCantidad(int cantidad) {
        return "[{\"productoId\":\"" + PRODUCTO_ID + "\",\"cantidad\":" + cantidad + "}]";
    }
}
