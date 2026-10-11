package com.foodstore.infrastructure.persistence;


import com.foodstore.domain.productos.application.port.ProductoRepositoryPort;
import com.foodstore.domain.productos.domain.model.CategoriaId;
import com.foodstore.domain.productos.domain.model.Precio;
import com.foodstore.domain.productos.domain.model.Producto;
import com.foodstore.domain.productos.domain.model.ProductoId;
import com.foodstore.domain.productos.domain.model.Sku;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dos transacciones reales sobre el mismo producto (sin @Transactional en la clase: cada TransactionTemplate abre y
 * confirma la suya). Reproduce el riesgo del stock: dos escritores que leen la misma cantidad y descuentan a la vez.
 * Sin @Version el segundo pisaria al primero en silencio y se venderian unidades que no existen.
 */
@SpringBootTest
@Testcontainers
// Esta suite no levanta RabbitMQ: el listener no arranca y el puerto invalido garantiza que, pase lo que pase,
// nunca se conecte al RabbitMQ de desarrollo (declararia colas ni consumiria eventos reales).
@TestPropertySource(properties = {"spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.port=1"})
class ProductoConcurrenciaIntegrationTest {

    private static final CategoriaId CATEGORIA = CategoriaId.de(UUID.randomUUID());

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ProductoRepositoryPort productoRepositoryPort;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void elSegundoEnConfirmarFalla_YNoPisaElDescuentoDelPrimero() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ProductoId id = crearConStock(tx, 5);

        assertThatThrownBy(() -> tx.executeWithoutResult(estadoA -> {
            Producto leidoPorA = productoRepositoryPort.buscarPorId(id).orElseThrow();

            // Mientras A calcula, B descuenta 3 y confirma.
            TransactionTemplate txB = new TransactionTemplate(transactionManager);
            txB.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            txB.executeWithoutResult(estadoB -> {
                Producto leidoPorB = productoRepositoryPort.buscarPorId(id).orElseThrow();
                leidoPorB.reducirStock(3);
                productoRepositoryPort.guardar(leidoPorB);
            });

            // A leyo 5 y descuenta 4 sobre esa lectura vieja: no debe pisar el cambio de B.
            leidoPorA.reducirStock(4);
            productoRepositoryPort.guardar(leidoPorA);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(leerStock(tx, id)).isEqualTo(2);
    }

    @Test
    void sinConcurrencia_LasEscriturasSucesivasFuncionanNormalmente() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ProductoId id = crearConStock(tx, 10);

        for (int i = 0; i < 3; i++) {
            tx.executeWithoutResult(estado -> {
                Producto producto = productoRepositoryPort.buscarPorId(id).orElseThrow();
                producto.reducirStock(2);
                productoRepositoryPort.guardar(producto);
            });
        }

        assertThat(leerStock(tx, id)).isEqualTo(4);
    }

    @Test
    void dosDescuentosSimultaneosDeLaUltimaUnidad_SoloUnoConfirma() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ProductoId id = crearConStock(tx, 1);
        CyclicBarrier barrera = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futuros = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futuros.add(pool.submit(() -> {
                    try {
                        tx.executeWithoutResult(estado -> {
                            Producto producto = productoRepositoryPort.buscarPorId(id).orElseThrow();
                            esperar(barrera); // los dos ya leyeron stock = 1
                            producto.reducirStock(1);
                            productoRepositoryPort.guardar(producto);
                        });
                        return true;
                    } catch (OptimisticLockingFailureException e) {
                        return false;
                    }
                }));
            }
            long confirmados = 0;
            for (Future<Boolean> futuro : futuros) {
                if (futuro.get(30, TimeUnit.SECONDS)) confirmados++;
            }
            assertThat(confirmados).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        // Una unidad, un solo descuento: el stock queda en 0, nunca en negativo ni descontado dos veces.
        assertThat(leerStock(tx, id)).isZero();
    }

    private ProductoId crearConStock(TransactionTemplate tx, int unidades) {
        return tx.execute(estado -> {
            Producto producto = Producto.crear(Sku.de("CONC-" + UUID.randomUUID().toString().substring(0, 8)),
                    "Producto de concurrencia", "x", Precio.deClp(new BigDecimal("1000")), CATEGORIA);
            producto.aumentarStock(unidades);
            return productoRepositoryPort.guardar(producto).getId();
        });
    }

    private int leerStock(TransactionTemplate tx, ProductoId id) {
        return tx.execute(estado -> productoRepositoryPort.buscarPorId(id).orElseThrow().getStock().getCantidad());
    }

    private static void esperar(CyclicBarrier barrera) {
        try {
            barrera.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
