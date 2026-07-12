package com.foodstore.application.service;

import com.foodstore.application.port.in.ObtenerProductoUseCase;
import com.foodstore.domain.productos.application.port.ProductoRepositoryPort;
import com.foodstore.domain.productos.domain.exception.ProductoNoEncontradoException;
import com.foodstore.domain.productos.domain.model.Producto;
import com.foodstore.domain.productos.domain.model.ProductoId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ObtenerProductoService implements ObtenerProductoUseCase {

    private final ProductoRepositoryPort productoRepositoryPort;

    @Override
    public Producto obtener(ProductoId id) {
        return productoRepositoryPort.buscarPorId(id)
                .orElseThrow(() -> new ProductoNoEncontradoException("Producto no encontrado: " + id.getValor()));
    }
}
