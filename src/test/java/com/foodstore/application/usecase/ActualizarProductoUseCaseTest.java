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
import com.foodstore.infrastructure.rest.dto.ProductoUpdateRequest;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActualizarProductoUseCaseTest {

    private static final String CATEGORIA_ID = "b6f3c1e0-2a3b-4c5d-8e9f-0a1b2c3d4e5f";
    private static final String OTRA_CATEGORIA_ID = "1a2b3c4d-5e6f-4789-9abc-0123456789ab";

    @Mock
    private ProductoRepositoryPort productoRepositoryPort;

    private ActualizarProductoUseCase actualizarProductoUseCase;

    @BeforeEach
    void setUp() {
        actualizarProductoUseCase = new ActualizarProductoUseCase(productoRepositoryPort);
    }

    @Test
    void execute_DeberiaActualizarProducto_CuandoElProductoExiste() {
        ProductoId id = ProductoId.nuevo();
        Producto existente = crearProducto();
        ProductoUpdateRequest request = new ProductoUpdateRequest(
                "Manzana Fuji Premium",
                "Manzana fresca importada, calibre extra",
                new BigDecimal("1800"),
                "CLP",
                OTRA_CATEGORIA_ID
        );

        when(productoRepositoryPort.buscarPorId(id)).thenReturn(Optional.of(existente));
        when(productoRepositoryPort.guardar(any(Producto.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Producto resultado = actualizarProductoUseCase.execute(id, request);

        assertThat(resultado.getNombre()).isEqualTo(request.nombre());
        assertThat(resultado.getDescripcion()).isEqualTo(request.descripcion());
        assertThat(resultado.getPrecio().getMonto()).isEqualByComparingTo(request.precioMonto());
        assertThat(resultado.getPrecio().getMoneda().getCurrencyCode()).isEqualTo(request.moneda());
        assertThat(resultado.getCategoriaId().getValor().toString()).isEqualTo(request.categoriaId());

        verify(productoRepositoryPort).buscarPorId(id);
        verify(productoRepositoryPort).guardar(existente);
    }

    @Test
    void execute_DeberiaLanzarProductoNoEncontradoException_CuandoElProductoNoExiste() {
        ProductoId id = ProductoId.nuevo();
        ProductoUpdateRequest request = new ProductoUpdateRequest(
                "Manzana Fuji Premium",
                "Manzana fresca importada, calibre extra",
                new BigDecimal("1800"),
                "CLP",
                OTRA_CATEGORIA_ID
        );

        when(productoRepositoryPort.buscarPorId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> actualizarProductoUseCase.execute(id, request))
                .isInstanceOf(ProductoNoEncontradoException.class)
                .hasMessageContaining(id.getValor().toString());

        verify(productoRepositoryPort, never()).guardar(any(Producto.class));
    }

    private Producto crearProducto() {
        Sku sku = Sku.de("SKU-001");
        Precio precio = Precio.de(new BigDecimal("1500"), Currency.getInstance("CLP"));
        CategoriaId categoriaId = CategoriaId.de(CATEGORIA_ID);
        return Producto.crear(sku, "Manzana Fuji", "Manzana fresca importada", precio, categoriaId);
    }
}
