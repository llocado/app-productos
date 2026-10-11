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

    /**
     * Vuelca el estado del agregado sobre una entidad JPA ya gestionada (o recien creada). No se construye una
     * entidad nueva en cada guardado: con @Version, una entidad sin version se tomaria como nueva y el UPDATE
     * se convertiria en un INSERT duplicado.
     */
    public void volcarEnEntidad(Producto producto, ProductoEntity entity) {
        CategoriaId categoriaId = producto.getCategoriaId();
        entity.setId(producto.getId().getValor());
        entity.setSku(producto.getSku().getValor());
        entity.setNombre(producto.getNombre());
        entity.setDescripcion(producto.getDescripcion());
        entity.setPrecioMonto(producto.getPrecio().getMonto());
        entity.setPrecioMoneda(producto.getPrecio().getMoneda().getCurrencyCode());
        entity.setStock(producto.getStock().getCantidad());
        entity.setCategoriaId(categoriaId != null ? categoriaId.getValor() : null);
        entity.setActivo(producto.isActivo());
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
