package com.foodstore.application.port.in;

import com.foodstore.domain.productos.domain.model.Producto;
import com.foodstore.domain.productos.domain.model.ProductoId;

public interface ObtenerProductoUseCase {

    /**
     * @throws com.foodstore.domain.productos.domain.exception.ProductoNoEncontradoException
     *         si no existe un producto con ese id.
     */
    Producto obtener(ProductoId id);
}
