package com.foodstore.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.foodstore.domain.productos.application.port.ProductoRepositoryPort;
import com.foodstore.domain.productos.domain.model.CategoriaId;
import com.foodstore.domain.productos.domain.model.Precio;
import com.foodstore.domain.productos.domain.model.Producto;
import com.foodstore.domain.productos.domain.model.ProductoId;
import com.foodstore.domain.productos.domain.model.Sku;
import com.foodstore.infrastructure.messaging.config.RabbitMQConfig;
import com.foodstore.infrastructure.messaging.dto.EventEnvelope;
import com.foodstore.infrastructure.messaging.listener.PagoEventListener;
import com.foodstore.infrastructure.persistence.entity.EventoSalidaEntity;
import com.foodstore.infrastructure.persistence.repository.SpringDataEventoSalidaRepository;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Flujo real dentro del servicio: se publica pago.aprobado en RabbitMQ, el listener lo consume, el stock se descuenta
 * en PostgreSQL y, si no alcanza, el evento de salida (outbox) se publica y se lee de una cola de prueba, validado
 * contra el JSON Schema de docs/events/.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "productos.outbox.intervalo=200ms")
class StockPagosIntegrationTest {

    private static final CategoriaId CATEGORIA = CategoriaId.de(UUID.randomUUID());

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:4-management-alpine");

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private RabbitAdmin rabbitAdmin;
    @Autowired
    private TopicExchange productosEventsExchange;
    @Autowired
    private ProductoRepositoryPort productoRepositoryPort;
    @Autowired
    private SpringDataEventoSalidaRepository eventoSalidaRepository;
    @Autowired
    private PagoEventListener pagoEventListener;

    private String colaDePrueba;

    @AfterEach
    void borrarColaDePrueba() {
        if (colaDePrueba != null) rabbitAdmin.deleteQueue(colaDePrueba);
    }

    @Test
    void pagoAprobado_DeberiaDescontarElStockDeTodosLosProductos_YNoPublicarNada() throws Exception {
        enlazarColaDePrueba();
        Producto manzana = crearConStock(10), pera = crearConStock(4);

        UUID pedidoId = UUID.randomUUID();

        publicar(pagoAprobadoJson(UUID.randomUUID(), null, pedidoId, item(manzana, 3), item(pera, 4)));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(stockDe(manzana)).isEqualTo(7);
            assertThat(stockDe(pera)).isZero();
        });
        Thread.sleep(1000);
        // Se filtra por el pedido de este test: el contenedor es compartido y otros tests dejan eventos pendientes.
        assertThat(hayEventoDe(pedidoId)).as("el exito es silencioso: no se publica ningun evento").isFalse();
    }

    @Test
    void pagoAprobadoSinStockSuficiente_DeberiaNoDescontarNada_YPublicarUnRechazoValidoSegunElSchema() throws Exception {
        enlazarColaDePrueba();
        Producto manzana = crearConStock(10), pera = crearConStock(2);
        UUID pagoId = UUID.randomUUID(), pedidoId = UUID.randomUUID(), correlacion = UUID.randomUUID();

        publicar(pagoAprobadoJson(UUID.randomUUID(), correlacion, pedidoId, pagoId, item(manzana, 3), item(pera, 5)));

        String json = esperarEventoDe(pedidoId);
        assertThat(validarContraSchema(json)).isEmpty();
        JsonNode evento = objectMapper.readTree(json);
        assertThat(evento.get("eventType").asString()).isEqualTo("stock.descuento-rechazado");
        assertThat(evento.get("producer").asString()).isEqualTo("app-productos");
        assertThat(evento.get("correlationId").asString()).isEqualTo(correlacion.toString());
        assertThat(evento.get("payload").get("pagoId").asString()).isEqualTo(pagoId.toString());
        JsonNode faltantes = evento.get("payload").get("faltantes");
        assertThat(faltantes).hasSize(1);
        assertThat(faltantes.get(0).get("productoId").asString()).isEqualTo(pera.getId().getValor().toString());
        assertThat(faltantes.get(0).get("solicitado").asInt()).isEqualTo(5);
        assertThat(faltantes.get(0).get("disponible").asInt()).isEqualTo(2);

        assertThat(stockDe(manzana)).as("todo o nada").isEqualTo(10);
        assertThat(stockDe(pera)).isEqualTo(2);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(eventoSalidaRepository.findById(UUID.fromString(evento.get("eventId").asString())).orElseThrow().getPublicadoEn()).isNotNull());
    }

    @Test
    void elMismoPagoEntregadoDosVeces_DeberiaDescontarUnaSolaVez() throws Exception {
        Producto manzana = crearConStock(10);
        UUID eventId = UUID.randomUUID(), pedidoId = UUID.randomUUID();
        String cuerpo = pagoAprobadoJson(eventId, null, pedidoId, UUID.randomUUID(), item(manzana, 3));

        publicar(cuerpo);
        publicar(cuerpo);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(stockDe(manzana)).isEqualTo(7));
        Thread.sleep(1500);
        assertThat(stockDe(manzana)).as("la copia se descarta como duplicado").isEqualTo(7);
    }

    @Test
    void ochoEntregasSimultaneasDelMismoEvento_DeberianDescontarUnaVezSinErroresInesperados() throws Exception {
        Producto manzana = crearConStock(10);
        EventEnvelope envelope = objectMapper.readValue(
                pagoAprobadoJson(UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(), item(manzana, 3)), EventEnvelope.class);

        List<Throwable> errores = lanzarEnParalelo(8, () -> pagoEventListener.escucharPagoAprobado(envelope));

        // Una copia gana; las demas se descartan como duplicado o chocan con la version (se reintentarian y se descartarian).
        assertThat(errores).allMatch(e -> e instanceof OptimisticLockingFailureException);
        assertThat(stockDe(manzana)).as("se descuenta una sola vez").isEqualTo(7);
    }

    @Test
    void dosPagosSimultaneosPorLaUltimaUnidad_UnoDescuentaYElOtroSeRechaza_SinStockNegativo() throws Exception {
        enlazarColaDePrueba();
        Producto ultima = crearConStock(1);
        UUID pedidoA = UUID.randomUUID(), pedidoB = UUID.randomUUID();
        EventEnvelope a = objectMapper.readValue(pagoAprobadoJson(UUID.randomUUID(), null, pedidoA, UUID.randomUUID(), item(ultima, 1)), EventEnvelope.class);
        EventEnvelope b = objectMapper.readValue(pagoAprobadoJson(UUID.randomUUID(), null, pedidoB, UUID.randomUUID(), item(ultima, 1)), EventEnvelope.class);

        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<?>> tareas = new ArrayList<>();
        for (EventEnvelope envelope : List.of(a, b)) {
            tareas.add(pool.submit(() -> {
                salida.await();
                reintentando(envelope); // emula la politica de reintentos de Spring ante un conflicto de version
                return null;
            }));
        }
        salida.countDown();
        for (Future<?> tarea : tareas) tarea.get();
        pool.shutdown();

        assertThat(stockDe(ultima)).as("una unidad, un solo descuento").isZero();
        // Exactamente uno de los dos pedidos recibe el rechazo.
        assertThat(pedidosConRechazo(List.of(pedidoA, pedidoB))).hasSize(1);
    }

    @Test
    void sinNingunaColaEnlazada_ElRechazoQuedaPendienteYSePublicaCuandoAparece() throws Exception {
        Producto sinStock = crearConStock(0);
        UUID pedidoId = UUID.randomUUID(), pagoId = UUID.randomUUID();

        publicar(pagoAprobadoJson(UUID.randomUUID(), null, pedidoId, pagoId, item(sinStock, 1)));

        UUID eventoId = UUID.nameUUIDFromBytes(("stock.descuento-rechazado:" + pagoId).getBytes(StandardCharsets.UTF_8));
        await().atMost(Duration.ofSeconds(10)).until(() -> eventoSalidaRepository.findById(eventoId).isPresent());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            EventoSalidaEntity fila = eventoSalidaRepository.findById(eventoId).orElseThrow();
            assertThat(fila.getPublicadoEn()).as("sin cola enlazada no debe darse por publicado").isNull();
            assertThat(fila.getIntentos()).as("debe haber reintentado").isGreaterThanOrEqualTo(1);
        });

        enlazarColaDePrueba();

        assertThat(validarContraSchema(esperarEventoDe(pedidoId))).isEmpty();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(eventoSalidaRepository.findById(eventoId).orElseThrow().getPublicadoEn()).isNotNull());
    }

    // ---------------------------------------------------------------- helpers

    private void reintentando(EventEnvelope envelope) {
        for (int intento = 0; intento < 4; intento++) {
            try {
                pagoEventListener.escucharPagoAprobado(envelope);
                return;
            } catch (OptimisticLockingFailureException e) {
                // Spring reintentaria: la siguiente lectura ve el stock nuevo.
            }
        }
        throw new AssertionError("no se resolvio tras los reintentos");
    }

    private List<Throwable> lanzarEnParalelo(int hilos, Runnable tarea) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Throwable> errores = new CopyOnWriteArrayList<>();
        List<Future<?>> tareas = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            tareas.add(pool.submit(() -> {
                try {
                    salida.await();
                    tarea.run();
                } catch (Throwable e) {
                    errores.add(e);
                }
            }));
        }
        salida.countDown();
        for (Future<?> t : tareas) t.get();
        pool.shutdown();
        return errores;
    }

    private Producto crearConStock(int unidades) {
        Producto producto = Producto.crear(Sku.de("INT-" + UUID.randomUUID().toString().substring(0, 8)),
                "Producto de prueba", "x", Precio.deClp(new BigDecimal("1000")), CATEGORIA);
        if (unidades > 0) producto.aumentarStock(unidades);
        return productoRepositoryPort.guardar(producto);
    }

    private int stockDe(Producto producto) {
        return productoRepositoryPort.buscarPorId(ProductoId.de(producto.getId().getValor())).orElseThrow().getStock().getCantidad();
    }

    private void enlazarColaDePrueba() {
        colaDePrueba = "test.productos." + UUID.randomUUID();
        // Durable y sin autoDelete: RabbitMQ 4 rechaza colas transitorias no exclusivas. Se borra en @AfterEach.
        Queue cola = new Queue(colaDePrueba, true, false, false);
        rabbitAdmin.declareQueue(cola);
        Binding binding = BindingBuilder.bind(cola).to(productosEventsExchange).with("stock.*");
        rabbitAdmin.declareBinding(binding);
    }

    private String esperarEventoDe(UUID pedidoId) {
        long limite = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < limite) {
            Message m = rabbitTemplate.receive(colaDePrueba, 500);
            if (m == null) continue;
            String json = new String(m.getBody(), StandardCharsets.UTF_8);
            if (objectMapper.readTree(json).get("payload").get("pedidoId").asString().equals(pedidoId.toString())) return json;
        }
        throw new AssertionError("No llego ningun evento para el pedido " + pedidoId);
    }

    /** Lee la cola de prueba una sola vez (sin descartar mensajes de otros pedidos) y devuelve los pedidos rechazados. */
    private List<UUID> pedidosConRechazo(List<UUID> candidatos) {
        List<UUID> rechazados = new ArrayList<>();
        long limite = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < limite) {
            // Con un rechazo ya visto, solo se espera un poco mas por si llegara uno de mas.
            Message m = rabbitTemplate.receive(colaDePrueba, rechazados.isEmpty() ? 500 : 1_500);
            if (m == null) {
                if (rechazados.isEmpty()) continue;
                break;
            }
            String json = new String(m.getBody(), StandardCharsets.UTF_8);
            UUID pedido = UUID.fromString(objectMapper.readTree(json).get("payload").get("pedidoId").asString());
            if (candidatos.contains(pedido) && !rechazados.contains(pedido)) rechazados.add(pedido);
        }
        return rechazados;
    }

    private boolean hayEventoDe(UUID pedidoId) {
        long limite = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < limite) {
            Message m = rabbitTemplate.receive(colaDePrueba, 500);
            if (m == null) continue;
            String json = new String(m.getBody(), StandardCharsets.UTF_8);
            if (objectMapper.readTree(json).get("payload").get("pedidoId").asString().equals(pedidoId.toString())) return true;
        }
        return false;
    }

    private List<Error> validarContraSchema(String json) throws Exception {
        Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(Files.readString(Path.of("docs/events/stock.descuento-rechazado.schema.json")));
        return schema.validate(json, InputFormat.JSON);
    }

    private void publicar(String cuerpo) {
        Message mensaje = MessageBuilder.withBody(cuerpo.getBytes(StandardCharsets.UTF_8)).setContentType("application/json").build();
        rabbitTemplate.send(RabbitMQConfig.EXCHANGE_PAGOS_EVENTS, RabbitMQConfig.ROUTING_KEY_PAGO_APROBADO, mensaje);
    }

    static String item(Producto producto, int cantidad) {
        return "{\"productoId\":\"" + producto.getId().getValor() + "\",\"cantidad\":" + cantidad + "}";
    }

    static String pagoAprobadoJson(UUID eventId, UUID correlationId, UUID pedidoId, String... items) {
        return pagoAprobadoJson(eventId, correlationId, pedidoId, UUID.randomUUID(), items);
    }

    static String pagoAprobadoJson(UUID eventId, UUID correlationId, UUID pedidoId, UUID pagoId, String... items) {
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"pago.aprobado\",\"eventVersion\":1,"
                + "\"occurredAt\":\"" + Instant.now() + "\",\"producer\":\"pagos-service\","
                + (correlationId == null ? "" : "\"correlationId\":\"" + correlationId + "\",")
                + "\"payload\":{\"pagoId\":\"" + pagoId + "\",\"pedidoId\":\"" + pedidoId + "\",\"carritoId\":\"" + UUID.randomUUID()
                + "\",\"usuarioId\":\"usuario-integ\",\"monto\":3000,\"moneda\":\"CLP\",\"items\":[" + String.join(",", items) + "]}}";
    }
}
