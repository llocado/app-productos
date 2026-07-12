package com.foodstore.application.service;

import com.foodstore.application.port.in.ListarProductosUseCase;
import com.foodstore.domain.productos.application.port.ProductoRepositoryPort;
import com.foodstore.domain.productos.domain.model.Producto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ListarProductosService implements ListarProductosUseCase {

    private final ProductoRepositoryPort productoRepositoryPort;

    @Override
    public List<Producto> listar() {
        return productoRepositoryPort.listarTodos();
    }
}
