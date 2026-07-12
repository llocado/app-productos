package com.foodstore.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodstore.domain.productos.application.port.ProductoRepositoryPort;
import com.foodstore.domain.productos.domain.exception.ProductoNoEncontradoException;
import com.foodstore.domain.productos.domain.model.CategoriaId;
import com.foodstore.domain.productos.domain.model.Precio;
import com.foodstore.domain.productos.domain.model.Producto;
import com.foodstore.domain.productos.domain.model.ProductoId;
import com.foodstore.domain.productos.domain.model.Sku;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ObtenerProductoUseCaseTest {

    private static final String CATEGORIA_ID = "b6f3c1e0-2a3b-4c5d-8e9f-0a1b2c3d4e5f";

    @Mock
    private ProductoRepositoryPort productoRepositoryPort;

    private ObtenerProductoUseCase obtenerProductoUseCase;

    @BeforeEach
    void setUp() {
        obtenerProductoUseCase = new ObtenerProductoUseCase(productoRepositoryPort);
    }

    @Test
    void execute_DeberiaRetornarProducto_CuandoExisteElId() {
        ProductoId id = ProductoId.nuevo();
        Producto producto = crearProducto();

        when(productoRepositoryPort.buscarPorId(id)).thenReturn(Optional.of(producto));

        Producto resultado = obtenerProductoUseCase.execute(id);

        assertThat(resultado).isSameAs(producto);
        verify(productoRepositoryPort).buscarPorId(id);
    }

    @Test
    void execute_DeberiaLanzarProductoNoEncontradoException_CuandoNoExisteElId() {
        ProductoId id = ProductoId.nuevo();

        when(productoRepositoryPort.buscarPorId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> obtenerProductoUseCase.execute(id))
                .isInstanceOf(ProductoNoEncontradoException.class)
                .hasMessageContaining(id.getValor().toString());

        verify(productoRepositoryPort).buscarPorId(id);
    }

    private Producto crearProducto() {
        Sku sku = Sku.de("SKU-001");
        Precio precio = Precio.de(new BigDecimal("1500"), Currency.getInstance("CLP"));
        CategoriaId categoriaId = CategoriaId.de(CATEGORIA_ID);
        return Producto.crear(sku, "Manzana Fuji", "Manzana fresca importada", precio, categoriaId);
    }
}
