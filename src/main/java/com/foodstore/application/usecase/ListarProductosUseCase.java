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

    public Pagina<Producto> execute(CriterioPaginacion criterio) {
        return productoRepositoryPort.listarPaginado(criterio);
    }
}
