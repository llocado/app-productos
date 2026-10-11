package com.foodstore.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodstore.application.port.EventoProcesadoPort;
import com.foodstore.application.port.EventoSalidaPort;
import com.foodstore.application.port.EventoSalidaPort.Faltante;
import com.foodstore.application.port.EventoSalidaPort.StockDescuentoRechazado;
import com.foodstore.application.usecase.DescontarStockPorPagoUseCase.Comando;
import com.foodstore.application.usecase.DescontarStockPorPagoUseCase.ItemComprado;
import com.foodstore.domain.productos.application.port.ProductoRepositoryPort;
import com.foodstore.domain.productos.domain.model.CategoriaId;
import com.foodstore.domain.productos.domain.model.Precio;
import com.foodstore.domain.productos.domain.model.Producto;
import com.foodstore.domain.productos.domain.model.Sku;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DescontarStockPorPagoUseCaseTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID CORRELACION = UUID.randomUUID();
    private static final UUID PAGO_ID = UUID.randomUUID();
    private static final UUID PEDIDO_ID = UUID.randomUUID();

    @Mock
    private ProductoRepositoryPort productoRepositoryPort;
    @Mock
    private EventoProcesadoPort eventoProcesadoPort;
    @Mock
    private EventoSalidaPort eventoSalidaPort;

    private DescontarStockPorPagoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DescontarStockPorPagoUseCase(productoRepositoryPort, eventoProcesadoPort, eventoSalidaPort);
    }

    @Test
    void execute_DeberiaDescontarTodosLosProductos_CuandoAlcanzaElStock() {
        Producto manzana = producto("MANZ-001", 10);
        Producto pera = producto("PERA-001", 4);
        encontrar(manzana);
        encontrar(pera);

        useCase.execute(comando(new ItemComprado(manzana.getId().getValor(), 3), new ItemComprado(pera.getId().getValor(), 4)));

        assertThat(manzana.getStock().getCantidad()).isEqualTo(7);
        assertThat(pera.getStock().getCantidad()).isZero();
        verify(productoRepositoryPort).guardar(manzana);
        verify(productoRepositoryPort).guardar(pera);
        verify(eventoSalidaPort, never()).registrar(any(StockDescuentoRechazado.class), any());
        verify(eventoProcesadoPort).marcarComoProcesado(EVENT_ID);
    }

    @Test
    void execute_NoDeberiaDescontarNada_YDeberiaRegistrarElRechazo_CuandoAUnProductoLeFaltaStock() {
        Producto manzana = producto("MANZ-001", 10);
        Producto pera = producto("PERA-001", 2);
        encontrar(manzana);
        encontrar(pera);

        useCase.execute(comando(new ItemComprado(manzana.getId().getValor(), 3), new ItemComprado(pera.getId().getValor(), 5)));

        assertThat(manzana.getStock().getCantidad()).as("todo o nada: la manzana tampoco se descuenta").isEqualTo(10);
        assertThat(pera.getStock().getCantidad()).isEqualTo(2);
        verify(productoRepositoryPort, never()).guardar(any());

        ArgumentCaptor<StockDescuentoRechazado> evento = forClass(StockDescuentoRechazado.class);
        verify(eventoSalidaPort).registrar(evento.capture(), org.mockito.ArgumentMatchers.eq(CORRELACION));
        assertThat(evento.getValue().pagoId()).isEqualTo(PAGO_ID);
        assertThat(evento.getValue().pedidoId()).isEqualTo(PEDIDO_ID);
        assertThat(evento.getValue().faltantes()).containsExactly(new Faltante(pera.getId().getValor(), 5, 2));
        verify(eventoProcesadoPort).marcarComoProcesado(EVENT_ID);
    }

    @Test
    void execute_DeberiaAprobarUnDescuentoQueDejaElStockExactamenteEnCero() {
        Producto manzana = producto("MANZ-001", 3);
        encontrar(manzana);

        useCase.execute(comando(new ItemComprado(manzana.getId().getValor(), 3)));

        assertThat(manzana.getStock().getCantidad()).isZero();
        verify(eventoSalidaPort, never()).registrar(any(StockDescuentoRechazado.class), any());
    }

    @Test
    void execute_DeberiaRechazarPorUnaUnidad_CuandoSePideUnaMasDeLaDisponible() {
        Producto manzana = producto("MANZ-001", 3);
        encontrar(manzana);

        useCase.execute(comando(new ItemComprado(manzana.getId().getValor(), 4)));

        assertThat(manzana.getStock().getCantidad()).isEqualTo(3);
        verify(eventoSalidaPort).registrar(any(StockDescuentoRechazado.class), any());
    }

    @Test
    void execute_DeberiaRechazarConDisponibleCero_CuandoElProductoNoExiste() {
        UUID inexistente = UUID.randomUUID();
        when(productoRepositoryPort.buscarPorId(com.foodstore.domain.productos.domain.model.ProductoId.de(inexistente)))
                .thenReturn(Optional.empty());

        useCase.execute(comando(new ItemComprado(inexistente, 2)));

        ArgumentCaptor<StockDescuentoRechazado> evento = forClass(StockDescuentoRechazado.class);
        verify(eventoSalidaPort).registrar(evento.capture(), any());
        assertThat(evento.getValue().faltantes()).containsExactly(new Faltante(inexistente, 2, 0));
    }

    @Test
    void execute_DeberiaRechazarConDisponibleCero_CuandoElProductoEstaInactivo() {
        Producto inactivo = producto("MANZ-001", 10);
        inactivo.desactivar();
        encontrar(inactivo);

        useCase.execute(comando(new ItemComprado(inactivo.getId().getValor(), 1)));

        ArgumentCaptor<StockDescuentoRechazado> evento = forClass(StockDescuentoRechazado.class);
        verify(eventoSalidaPort).registrar(evento.capture(), any());
        assertThat(evento.getValue().faltantes()).containsExactly(new Faltante(inactivo.getId().getValor(), 1, 0));
        assertThat(inactivo.getStock().getCantidad()).isEqualTo(10);
    }

    @Test
    void execute_DeberiaSumarLasCantidades_CuandoUnProductoViajaRepetido() {
        Producto manzana = producto("MANZ-001", 5);
        encontrar(manzana);

        useCase.execute(comando(new ItemComprado(manzana.getId().getValor(), 2), new ItemComprado(manzana.getId().getValor(), 3)));

        assertThat(manzana.getStock().getCantidad()).isZero();
        verify(productoRepositoryPort).guardar(manzana);
    }

    @Test
    void execute_DeberiaRechazar_CuandoLaSumaDeUnProductoRepetidoNoAlcanza() {
        Producto manzana = producto("MANZ-001", 4);
        encontrar(manzana);

        useCase.execute(comando(new ItemComprado(manzana.getId().getValor(), 2), new ItemComprado(manzana.getId().getValor(), 3)));

        assertThat(manzana.getStock().getCantidad()).isEqualTo(4);
        verify(eventoSalidaPort).registrar(any(StockDescuentoRechazado.class), any());
    }

    @Test
    void execute_NoDeberiaHacerNada_CuandoElEventoYaFueProcesado() {
        when(eventoProcesadoPort.yaFueProcesado(EVENT_ID)).thenReturn(true);

        useCase.execute(comando(new ItemComprado(UUID.randomUUID(), 1)));

        verify(productoRepositoryPort, never()).buscarPorId(any());
        verify(productoRepositoryPort, never()).guardar(any());
        verify(eventoSalidaPort, never()).registrar(any(StockDescuentoRechazado.class), any());
        verify(eventoProcesadoPort, never()).marcarComoProcesado(any());
    }

    @Test
    void yaProcesado_DeberiaDelegarEnLaLibreta() {
        when(eventoProcesadoPort.yaFueProcesado(EVENT_ID)).thenReturn(true);

        assertThat(useCase.yaProcesado(EVENT_ID)).isTrue();
    }

    private void encontrar(Producto producto) {
        when(productoRepositoryPort.buscarPorId(producto.getId())).thenReturn(Optional.of(producto));
    }

    private static Comando comando(ItemComprado... items) {
        return new Comando(EVENT_ID, CORRELACION, PAGO_ID, PEDIDO_ID, "usuario-1", List.of(items));
    }

    private static Producto producto(String sku, int stock) {
        Producto producto = Producto.crear(Sku.de(sku), "Producto " + sku, "x", Precio.deClp(new BigDecimal("1000")),
                CategoriaId.de(UUID.randomUUID()));
        if (stock > 0) producto.aumentarStock(stock);
        return producto;
    }
}
