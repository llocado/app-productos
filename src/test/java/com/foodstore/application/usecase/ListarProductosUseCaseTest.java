package com.foodstore.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodstore.domain.productos.application.port.ProductoRepositoryPort;
import com.foodstore.domain.productos.domain.model.CategoriaId;
import com.foodstore.domain.productos.domain.model.CriterioPaginacion;
import com.foodstore.domain.productos.domain.model.Pagina;
import com.foodstore.domain.productos.domain.model.Precio;
import com.foodstore.domain.productos.domain.model.Producto;
import com.foodstore.domain.productos.domain.model.Sku;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListarProductosUseCaseTest {

    private static final String CATEGORIA_ID = "b6f3c1e0-2a3b-4c5d-8e9f-0a1b2c3d4e5f";

    @Mock
    private ProductoRepositoryPort productoRepositoryPort;

    private ListarProductosUseCase listarProductosUseCase;

    @BeforeEach
    void setUp() {
        listarProductosUseCase = new ListarProductosUseCase(productoRepositoryPort);
    }

    @Test
    void execute_DeberiaRetornarLaPaginaDelPuerto_CuandoExistenProductosRegistrados() {
        Producto producto1 = crearProducto("SKU-001", "Manzana Fuji");
        Producto producto2 = crearProducto("SKU-002", "Pera Williams");
        CriterioPaginacion criterio = CriterioPaginacion.de(0, 20);
        Pagina<Producto> pagina = Pagina.de(List.of(producto1, producto2), 0, 20, 2);

        when(productoRepositoryPort.listarPaginado(criterio)).thenReturn(pagina);

        Pagina<Producto> resultado = listarProductosUseCase.execute(criterio, null);

        assertThat(resultado.getContenido()).containsExactly(producto1, producto2);
        assertThat(resultado.getTotalElementos()).isEqualTo(2);
        verify(productoRepositoryPort).listarPaginado(criterio);
    }

    @Test
    void execute_DeberiaRetornarPaginaVacia_CuandoNoExistenProductosRegistrados() {
        CriterioPaginacion criterio = CriterioPaginacion.de(0, 20);
        Pagina<Producto> paginaVacia = Pagina.de(List.of(), 0, 20, 0);

        when(productoRepositoryPort.listarPaginado(criterio)).thenReturn(paginaVacia);

        Pagina<Producto> resultado = listarProductosUseCase.execute(criterio, null);

        assertThat(resultado.getContenido()).isEmpty();
        verify(productoRepositoryPort).listarPaginado(criterio);
    }

    @Test
    void execute_DeberiaBuscarPorNombre_CuandoSeIndicaUnTexto() {
        Producto leche = crearProducto("SKU-003", "Leche Entera");
        CriterioPaginacion criterio = CriterioPaginacion.de(0, 20);
        Pagina<Producto> pagina = Pagina.de(List.of(leche), 0, 20, 1);
        when(productoRepositoryPort.buscarPorNombre("lech", criterio)).thenReturn(pagina);

        Pagina<Producto> resultado = listarProductosUseCase.execute(criterio, "lech");

        assertThat(resultado.getContenido()).containsExactly(leche);
        verify(productoRepositoryPort).buscarPorNombre("lech", criterio);
        verify(productoRepositoryPort, never()).listarPaginado(criterio);
    }

    @Test
    void execute_DeberiaQuitarLosEspaciosDelTexto_AntesDeBuscar() {
        CriterioPaginacion criterio = CriterioPaginacion.de(0, 20);
        when(productoRepositoryPort.buscarPorNombre("lech", criterio)).thenReturn(Pagina.de(List.of(), 0, 20, 0));

        listarProductosUseCase.execute(criterio, "  lech  ");

        verify(productoRepositoryPort).buscarPorNombre("lech", criterio);
    }

    @Test
    void execute_DeberiaListarTodo_CuandoElTextoEstaEnBlanco() {
        CriterioPaginacion criterio = CriterioPaginacion.de(0, 20);
        when(productoRepositoryPort.listarPaginado(criterio)).thenReturn(Pagina.de(List.of(), 0, 20, 0));

        listarProductosUseCase.execute(criterio, "   ");

        verify(productoRepositoryPort).listarPaginado(criterio);
        verify(productoRepositoryPort, never()).buscarPorNombre(any(), any());
    }

    private Producto crearProducto(String sku, String nombre) {
        Sku skuValor = Sku.de(sku);
        Precio precio = Precio.de(new BigDecimal("1500"), Currency.getInstance("CLP"));
        CategoriaId categoriaId = CategoriaId.de(CATEGORIA_ID);
        return Producto.crear(skuValor, nombre, "Descripcion de " + nombre, precio, categoriaId);
    }
}
