package com.foodstore.application.port.in;

import com.foodstore.domain.productos.domain.model.Producto;

public interface CrearProductoUseCase {

    Producto crear(CrearProductoCommand command);
}
