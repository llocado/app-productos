package com.foodstore.application.usecase;

import com.foodstore.domain.productos.application.port.ProductoRepositoryPort;
import com.foodstore.domain.productos.domain.model.CriterioPaginacion;
import com.foodstore.domain.productos.domain.model.Pagina;
import com.foodstore.domain.productos.domain.model.Producto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public final class ListarProductosUseCase {

    private final ProductoRepositoryPort productoRepositoryPort;

    /** Sin nombre (nulo o en blanco) lista todo el catalogo; con nombre, solo los productos que lo contienen. */
    public Pagina<Producto> execute(CriterioPaginacion criterio, String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return productoRepositoryPort.listarPaginado(criterio);
        }
        return productoRepositoryPort.buscarPorNombre(nombre.trim(), criterio);
    }
}
