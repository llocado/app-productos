package com.foodstore.application.usecase;

import com.foodstore.domain.productos.application.port.ProductoRepositoryPort;
import com.foodstore.domain.productos.domain.model.Producto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public final class ListarProductosUseCase {

    private final ProductoRepositoryPort productoRepositoryPort;

    public List<Producto> execute() {
        return productoRepositoryPort.listarTodos();
    }
}
