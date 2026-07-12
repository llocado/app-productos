package com.foodstore.application.usecase;

import com.foodstore.domain.productos.application.port.ProductoRepositoryPort;
import com.foodstore.domain.productos.domain.exception.ProductoNoEncontradoException;
import com.foodstore.domain.productos.domain.model.Producto;
import com.foodstore.domain.productos.domain.model.ProductoId;
import com.foodstore.domain.productos.domain.model.Sku;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public final class ObtenerProductoUseCase {

    private final ProductoRepositoryPort productoRepositoryPort;

    /**
     * @throws ProductoNoEncontradoException si no existe un producto con ese id.
     */
    public Producto execute(ProductoId id) {
        return productoRepositoryPort.buscarPorId(id)
                .orElseThrow(() -> new ProductoNoEncontradoException("Producto no encontrado: " + id.getValor()));
    }

    /**
     * @throws ProductoNoEncontradoException si no existe un producto con ese sku.
     */
    public Producto execute(Sku sku) {
        return productoRepositoryPort.buscarPorSku(sku)
                .orElseThrow(() -> new ProductoNoEncontradoException("Producto no encontrado con SKU: " + sku.getValor()));
    }
}
