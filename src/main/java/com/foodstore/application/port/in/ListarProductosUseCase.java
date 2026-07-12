package com.foodstore.application.port.in;

import com.foodstore.domain.productos.domain.model.Producto;
import java.util.List;

public interface ListarProductosUseCase {

    List<Producto> listar();
}
