package com.foodstore.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
class EliminarProductoUseCaseTest {

    private static final String CATEGORIA_ID = "b6f3c1e0-2a3b-4c5d-8e9f-0a1b2c3d4e5f";

    @Mock
    private ProductoRepositoryPort productoRepositoryPort;

    private EliminarProductoUseCase eliminarProductoUseCase;

    @BeforeEach
    void setUp() {
        eliminarProductoUseCase = new EliminarProductoUseCase(productoRepositoryPort);
    }

    @Test
    void execute_DeberiaEliminarProducto_CuandoElProductoExiste() {
        ProductoId id = ProductoId.nuevo();
        Producto existente = crearProducto();

        when(productoRepositoryPort.buscarPorId(id)).thenReturn(Optional.of(existente));

        eliminarProductoUseCase.execute(id);

        verify(productoRepositoryPort).buscarPorId(id);
        verify(productoRepositoryPort).eliminar(id);
    }

    @Test
    void execute_DeberiaLanzarProductoNoEncontradoException_CuandoElProductoNoExiste() {
        ProductoId id = ProductoId.nuevo();

        when(productoRepositoryPort.buscarPorId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eliminarProductoUseCase.execute(id))
                .isInstanceOf(ProductoNoEncontradoException.class)
                .hasMessageContaining(id.getValor().toString());

        verify(productoRepositoryPort, never()).eliminar(any(ProductoId.class));
    }

    private Producto crearProducto() {
        Sku sku = Sku.de("SKU-001");
        Precio precio = Precio.de(new BigDecimal("1500"), Currency.getInstance("CLP"));
        CategoriaId categoriaId = CategoriaId.de(CATEGORIA_ID);
        return Producto.crear(sku, "Manzana Fuji", "Manzana fresca importada", precio, categoriaId);
    }
}
