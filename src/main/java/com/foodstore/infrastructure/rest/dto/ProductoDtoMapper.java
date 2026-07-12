package com.foodstore.infrastructure.rest.dto;

import com.foodstore.domain.productos.domain.model.CategoriaId;
import com.foodstore.domain.productos.domain.model.Producto;
import org.springframework.stereotype.Component;

@Component
public class ProductoDtoMapper {

    public ProductoResponse toResponse(Producto producto) {
        CategoriaId categoriaId = producto.getCategoriaId();
        return new ProductoResponse(
                producto.getId().getValor().toString(),
                producto.getSku().getValor(),
                producto.getNombre(),
                producto.getDescripcion(),
                producto.getPrecio().getMonto(),
                producto.getPrecio().getMoneda().getCurrencyCode(),
                producto.getStock().getCantidad(),
                categoriaId != null ? categoriaId.getValor().toString() : null,
                producto.isActivo()
        );
    }
}
