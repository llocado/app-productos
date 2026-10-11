package com.foodstore.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngresarStockUseCaseTest {

    @Mock
    private ProductoRepositoryPort productoRepositoryPort;

    private IngresarStockUseCase ingresarStockUseCase;

    @BeforeEach
    void setUp() {
        ingresarStockUseCase = new IngresarStockUseCase(productoRepositoryPort);
    }

    @Test
    void execute_DeberiaSumarLasUnidadesAlStockYGuardar_CuandoElProductoExiste() {
        Producto producto = productoSinStock();
        when(productoRepositoryPort.buscarPorId(producto.getId())).thenReturn(Optional.of(producto));
        when(productoRepositoryPort.guardar(producto)).thenReturn(producto);

        Producto resultado = ingresarStockUseCase.execute(producto.getId(), 30);

        assertThat(resultado.getStock().getCantidad()).isEqualTo(30);
        verify(productoRepositoryPort).guardar(producto);
    }

    @Test
    void execute_DeberiaAcumular_CuandoYaHabiaStock() {
        Producto producto = productoSinStock();
        producto.aumentarStock(5);
        when(productoRepositoryPort.buscarPorId(producto.getId())).thenReturn(Optional.of(producto));
        when(productoRepositoryPort.guardar(producto)).thenReturn(producto);

        assertThat(ingresarStockUseCase.execute(producto.getId(), 7).getStock().getCantidad()).isEqualTo(12);
    }

    @Test
    void execute_DeberiaLanzarProductoNoEncontrado_CuandoNoExiste() {
        ProductoId id = ProductoId.de(UUID.randomUUID());
        when(productoRepositoryPort.buscarPorId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ingresarStockUseCase.execute(id, 10))
                .isInstanceOf(ProductoNoEncontradoException.class)
                .hasMessageContaining(id.getValor().toString());
        verify(productoRepositoryPort, never()).guardar(any());
    }

    @Test
    void execute_NoDeberiaGuardar_CuandoLasUnidadesNoSonPositivas() {
        Producto producto = productoSinStock();
        when(productoRepositoryPort.buscarPorId(producto.getId())).thenReturn(Optional.of(producto));

        assertThatThrownBy(() -> ingresarStockUseCase.execute(producto.getId(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        verify(productoRepositoryPort, never()).guardar(any());
    }

    private static Producto productoSinStock() {
        return Producto.crear(Sku.de("SKU-001"), "Manzana Fuji", "Manzana fresca importada",
                Precio.deClp(new BigDecimal("1500")), CategoriaId.de(UUID.randomUUID()));
    }
}
