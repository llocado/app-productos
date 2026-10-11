package com.foodstore.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.foodstore.application.usecase.DescontarStockPorPagoUseCase;
import com.foodstore.domain.productos.application.port.ProductoRepositoryPort;
import com.foodstore.domain.productos.domain.model.CategoriaId;
import com.foodstore.domain.productos.domain.model.Precio;
import com.foodstore.domain.productos.domain.model.Producto;
import com.foodstore.domain.productos.domain.model.ProductoId;
import com.foodstore.domain.productos.domain.model.Sku;
import com.foodstore.infrastructure.messaging.config.RabbitMQConfig;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Mensajes venenosos contra RabbitMQ real: deben terminar en la DLQ, con el numero esperado de intentos, sin bloquear
 * la cola. Las esperas entre reintentos se acortan solo para que el test no tarde.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "spring.rabbitmq.listener.simple.retry.initial-interval=50ms",
        "spring.rabbitmq.listener.simple.retry.max-interval=200ms"
})
class StockPoisonMessageIntegrationTest {

    private static final String DLQ = "productos.pago-aprobado.dlq";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:4-management-alpine");

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private RabbitAdmin rabbitAdmin;
    @Autowired
    private ProductoRepositoryPort productoRepositoryPort;

    @MockitoSpyBean
    private DescontarStockPorPagoUseCase descontarStockPorPagoUseCase;

    @BeforeEach
    void limpiarColas() {
        rabbitAdmin.purgeQueue(RabbitMQConfig.QUEUE_PAGO_APROBADO, false);
        rabbitAdmin.purgeQueue(DLQ, false);
    }

    @Test
    void payloadInvalido_DeberiaIrALaDlqSinReintentosYSinTocarElStock() {
        UUID eventId = UUID.randomUUID();

        publicar("{\"eventId\":\"" + eventId + "\",\"eventType\":\"pago.aprobado\",\"eventVersion\":1,"
                + "\"occurredAt\":\"2026-10-04T12:00:00Z\",\"producer\":\"pagos-service\","
                + "\"payload\":{\"pedidoId\":\"" + UUID.randomUUID() + "\",\"usuarioId\":\"u1\"}}");

        Message enDlq = rabbitTemplate.receive(DLQ, 10_000);
        assertThat(enDlq).as("mensaje en la DLQ").isNotNull();
        assertThat(new String(enDlq.getBody(), StandardCharsets.UTF_8)).contains(eventId.toString());
        verify(descontarStockPorPagoUseCase, never()).execute(any());
        assertThat(rabbitAdmin.getQueueInfo(RabbitMQConfig.QUEUE_PAGO_APROBADO).getMessageCount()).isZero();
    }

    @Test
    void jsonRoto_DeberiaIrALaDlq() {
        publicar("esto{no es json");

        assertThat(rabbitTemplate.receive(DLQ, 10_000)).as("mensaje en la DLQ").isNotNull();
        assertThat(rabbitAdmin.getQueueInfo(RabbitMQConfig.QUEUE_PAGO_APROBADO).getMessageCount()).isZero();
    }

    @Test
    void falloTransitorioPersistente_DeberiaHacer1IntentoY3ReintentosYLuegoIrALaDlq() throws Exception {
        doThrow(new IllegalStateException("fallo transitorio simulado")).when(descontarStockPorPagoUseCase).execute(any());

        publicar(StockPagosIntegrationTest.pagoAprobadoJson(UUID.randomUUID(), null, UUID.randomUUID(),
                "{\"productoId\":\"" + UUID.randomUUID() + "\",\"cantidad\":1}"));

        assertThat(rabbitTemplate.receive(DLQ, 10_000)).as("mensaje en la DLQ tras agotar reintentos").isNotNull();
        verify(descontarStockPorPagoUseCase, times(4)).execute(any());

        Thread.sleep(1500);
        verify(descontarStockPorPagoUseCase, times(4)).execute(any());
        assertThat(rabbitAdmin.getQueueInfo(RabbitMQConfig.QUEUE_PAGO_APROBADO).getMessageCount()).isZero();
    }

    @Test
    void unMensajeVenenoso_NoDeberiaBloquearLaColaParaLosMensajesValidosSiguientes() {
        publicar("{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"pago.aprobado\",\"eventVersion\":1,"
                + "\"occurredAt\":\"2026-10-04T12:00:00Z\",\"producer\":\"pagos-service\",\"payload\":{}}");
        Producto producto = Producto.crear(Sku.de("POI-" + UUID.randomUUID().toString().substring(0, 8)), "Producto", "x",
                Precio.deClp(new BigDecimal("1000")), CategoriaId.de(UUID.randomUUID()));
        producto.aumentarStock(5);
        Producto guardado = productoRepositoryPort.guardar(producto);
        publicar(StockPagosIntegrationTest.pagoAprobadoJson(UUID.randomUUID(), null, UUID.randomUUID(),
                StockPagosIntegrationTest.item(guardado, 2)));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(productoRepositoryPort.buscarPorId(ProductoId.de(guardado.getId().getValor())).orElseThrow()
                        .getStock().getCantidad()).isEqualTo(3));
        assertThat(rabbitTemplate.receive(DLQ, 5_000)).as("el venenoso quedo en la DLQ").isNotNull();
    }

    private void publicar(String cuerpo) {
        Message mensaje = MessageBuilder.withBody(cuerpo.getBytes(StandardCharsets.UTF_8)).setContentType("application/json").build();
        rabbitTemplate.send(RabbitMQConfig.EXCHANGE_PAGOS_EVENTS, RabbitMQConfig.ROUTING_KEY_PAGO_APROBADO, mensaje);
    }
}
