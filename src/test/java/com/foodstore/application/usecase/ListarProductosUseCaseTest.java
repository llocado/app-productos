package com.foodstore.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodstore.domain.productos.application.port.ProductoRepositoryPort;
import com.foodstore.domain.productos.domain.model.CategoriaId;
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
    void execute_DeberiaRetornarListaCompleta_CuandoExistenProductosRegistrados() {
        Producto producto1 = crearProducto("SKU-001", "Manzana Fuji");
        Producto producto2 = crearProducto("SKU-002", "Pera Williams");

        when(productoRepositoryPort.listarTodos()).thenReturn(List.of(producto1, producto2));

        List<Producto> resultado = listarProductosUseCase.execute();

        assertThat(resultado).hasSize(2).containsExactly(producto1, producto2);
        verify(productoRepositoryPort).listarTodos();
    }

    @Test
    void execute_DeberiaRetornarListaVacia_CuandoNoExistenProductosRegistrados() {
        when(productoRepositoryPort.listarTodos()).thenReturn(List.of());

        List<Producto> resultado = listarProductosUseCase.execute();

        assertThat(resultado).isEmpty();
        verify(productoRepositoryPort).listarTodos();
    }

    private Producto crearProducto(String sku, String nombre) {
        Sku skuValor = Sku.de(sku);
        Precio precio = Precio.de(new BigDecimal("1500"), Currency.getInstance("CLP"));
        CategoriaId categoriaId = CategoriaId.de(CATEGORIA_ID);
        return Producto.crear(skuValor, nombre, "Descripcion de " + nombre, precio, categoriaId);
    }
}
