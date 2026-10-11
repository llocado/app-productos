package com.foodstore.application.usecase;

import com.foodstore.application.port.EventoProcesadoPort;
import com.foodstore.application.port.EventoSalidaPort;
import com.foodstore.application.port.EventoSalidaPort.Faltante;
import com.foodstore.application.port.EventoSalidaPort.StockDescuentoRechazado;
import com.foodstore.domain.productos.application.port.ProductoRepositoryPort;
import com.foodstore.domain.productos.domain.model.Producto;
import com.foodstore.domain.productos.domain.model.ProductoId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// No es "final": @Transactional necesita que Spring genere un proxy CGLIB.
@Slf4j
@Service
@RequiredArgsConstructor
public class DescontarStockPorPagoUseCase {

    public record ItemComprado(UUID productoId, int cantidad) {
    }

    public record Comando(UUID eventId, UUID correlationId, UUID pagoId, UUID pedidoId, String usuarioId,
                          List<ItemComprado> items) {
    }

    private final ProductoRepositoryPort productoRepositoryPort;
    private final EventoProcesadoPort eventoProcesadoPort;
    private final EventoSalidaPort eventoSalidaPort;

    /**
     * Cuando un pago se aprueba (pago.aprobado) se descuenta el stock de lo comprado, TODO O NADA: primero se valida
     * cada producto y solo si todos alcanzan se descuenta. Si alguno no alcanza (o ya no existe, o esta inactivo) no
     * se toca nada y se registra stock.descuento-rechazado en el outbox para que pedidos-service marque el pedido
     * SIN_STOCK. Una sola transaccion: descuentos, evento de salida y marca de evento procesado se confirman juntos.
     *
     * <p>Idempotente por eventId (RabbitMQ entrega at-least-once; restar dos veces vendería unidades que no existen).
     * Concurrencia: cada producto lleva @Version; si otro pago lo modifico entre la lectura y la confirmacion, esta
     * transaccion falla con OptimisticLockingFailureException, se reintenta y valida contra el stock nuevo.
     * Los productos se recorren ordenados por id para que dos pagos que comparten productos tomen el mismo orden.
     */
    @Transactional
    public void execute(Comando comando) {
        if (eventoProcesadoPort.yaFueProcesado(comando.eventId())) {
            log.info("Evento ya procesado, se descarta el duplicado eventId={}", comando.eventId());
            return;
        }

        Map<UUID, Integer> pedido = agrupar(comando.items());
        Map<UUID, Producto> aDescontar = new TreeMap<>();
        List<Faltante> faltantes = new ArrayList<>();
        for (Map.Entry<UUID, Integer> linea : pedido.entrySet()) {
            Optional<Producto> producto = productoRepositoryPort.buscarPorId(ProductoId.de(linea.getKey()));
            if (producto.isPresent() && producto.get().tieneDisponibilidad(linea.getValue())) {
                aDescontar.put(linea.getKey(), producto.get());
            } else {
                int disponible = producto.filter(Producto::isActivo).map(p -> p.getStock().getCantidad()).orElse(0);
                faltantes.add(new Faltante(linea.getKey(), linea.getValue(), disponible));
            }
        }

        if (faltantes.isEmpty()) {
            aDescontar.forEach((id, producto) -> {
                producto.reducirStock(pedido.get(id));
                productoRepositoryPort.guardar(producto);
            });
            log.info("Stock descontado pagoId={} pedidoId={} productos={}", comando.pagoId(), comando.pedidoId(), aDescontar.size());
        } else {
            eventoSalidaPort.registrar(
                    new StockDescuentoRechazado(comando.pagoId(), comando.pedidoId(), comando.usuarioId(), faltantes),
                    comando.correlationId());
            log.warn("Stock insuficiente, no se descuenta nada pagoId={} pedidoId={} productosFaltantes={}",
                    comando.pagoId(), comando.pedidoId(), faltantes.size());
        }
        eventoProcesadoPort.marcarComoProcesado(comando.eventId());
    }

    /** Para el listener: tras un choque de unicidad concurrente, distingue "duplicado" de un error real. */
    @Transactional(readOnly = true)
    public boolean yaProcesado(UUID eventId) {
        return eventoProcesadoPort.yaFueProcesado(eventId);
    }

    /** Suma las cantidades de un mismo producto repetido y las ordena por id. */
    private static Map<UUID, Integer> agrupar(List<ItemComprado> items) {
        Map<UUID, Integer> agrupado = new TreeMap<>();
        for (ItemComprado item : items) {
            agrupado.merge(item.productoId(), item.cantidad(), Integer::sum);
        }
        return agrupado;
    }
}
