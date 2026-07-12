package com.foodstore.infrastructure.persistence.mapper;

import com.foodstore.domain.productos.domain.model.CategoriaId;
import com.foodstore.domain.productos.domain.model.Precio;
import com.foodstore.domain.productos.domain.model.Producto;
import com.foodstore.domain.productos.domain.model.ProductoId;
import com.foodstore.domain.productos.domain.model.Sku;
import com.foodstore.domain.productos.domain.model.Stock;
import com.foodstore.infrastructure.persistence.entity.ProductoEntity;
import java.util.Currency;
import org.springframework.stereotype.Component;

/**
 * Traduce entre el agregado de dominio (Producto) y la entidad JPA (ProductoEntity).
 * Producto no expone constructor ni builder publico: para rehidratar desde la base de
 * datos se usa Producto.reconstruir(...), reservado para eso (no dispara las reglas de
 * "alta" que aplica Producto.crear(...)).
 */
@Component
public class ProductoEntityMapper {

    public ProductoEntity toEntity(Producto producto) {
        if (producto == null) {
            return null;
        }
        CategoriaId categoriaId = producto.getCategoriaId();
        return ProductoEntity.builder()
                .id(producto.getId().getValor())
                .sku(producto.getSku().getValor())
                .nombre(producto.getNombre())
                .descripcion(producto.getDescripcion())
                .precioMonto(producto.getPrecio().getMonto())
                .precioMoneda(producto.getPrecio().getMoneda().getCurrencyCode())
                .stock(producto.getStock().getCantidad())
                .categoriaId(categoriaId != null ? categoriaId.getValor() : null)
                .activo(producto.isActivo())
                .build();
    }

    public Producto toDomain(ProductoEntity entity) {
        if (entity == null) {
            return null;
        }
        return Producto.reconstruir(
                ProductoId.de(entity.getId()),
                Sku.de(entity.getSku()),
                entity.getNombre(),
                entity.getDescripcion(),
                Precio.de(entity.getPrecioMonto(), Currency.getInstance(entity.getPrecioMoneda())),
                Stock.de(entity.getStock()),
                entity.getCategoriaId() != null ? CategoriaId.de(entity.getCategoriaId()) : null,
                entity.isActivo()
        );
    }
}
