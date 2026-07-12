package com.foodstore.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodstore.domain.productos.application.port.ProductoRepositoryPort;
import com.foodstore.domain.productos.domain.exception.SkuDuplicadoException;
import com.foodstore.domain.productos.domain.model.Producto;
import com.foodstore.domain.productos.domain.model.Sku;
import com.foodstore.infrastructure.rest.dto.ProductoRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CrearProductoUseCaseTest {

    private static final String CATEGORIA_ID = "b6f3c1e0-2a3b-4c5d-8e9f-0a1b2c3d4e5f";

    @Mock
    private ProductoRepositoryPort productoRepositoryPort;

    private CrearProductoUseCase crearProductoUseCase;

    @BeforeEach
    void setUp() {
        crearProductoUseCase = new CrearProductoUseCase(productoRepositoryPort);
    }

    @Test
    void execute_DeberiaCrearProducto_CuandoRequestEsValido() {
        ProductoRequest request = new ProductoRequest(
                "SKU-001",
                "Manzana Fuji",
                "Manzana fresca importada",
                new BigDecimal("1500"),
                "CLP",
                CATEGORIA_ID
        );

        when(productoRepositoryPort.existePorSku(any(Sku.class))).thenReturn(false);
        when(productoRepositoryPort.guardar(any(Producto.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Producto resultado = crearProductoUseCase.execute(request);

        assertThat(resultado).isNotNull();
        assertThat(resultado.getSku().getValor()).isEqualTo(request.sku());
        assertThat(resultado.getNombre()).isEqualTo(request.nombre());
        assertThat(resultado.getDescripcion()).isEqualTo(request.descripcion());
        assertThat(resultado.getPrecio().getMonto()).isEqualByComparingTo(request.precioMonto());
        assertThat(resultado.getPrecio().getMoneda().getCurrencyCode()).isEqualTo(request.moneda());
        assertThat(resultado.getCategoriaId().getValor().toString()).isEqualTo(request.categoriaId());

        verify(productoRepositoryPort).existePorSku(any(Sku.class));
        verify(productoRepositoryPort).guardar(any(Producto.class));
    }

    @Test
    void execute_DeberiaLanzarExcepcion_CuandoSkuYaExiste() {
        ProductoRequest request = new ProductoRequest(
                "SKU-001",
                "Manzana Fuji",
                "Manzana fresca importada",
                new BigDecimal("1500"),
                "CLP",
                CATEGORIA_ID
        );

        when(productoRepositoryPort.existePorSku(any(Sku.class))).thenReturn(true);

        org.junit.jupiter.api.Assertions.assertThrows(
                SkuDuplicadoException.class,
                () -> crearProductoUseCase.execute(request)
        );

        verify(productoRepositoryPort, never()).guardar(any(Producto.class));
    }
}
