package com.foodstore.application.usecase;

import com.foodstore.domain.productos.application.port.ProductoRepositoryPort;
import com.foodstore.domain.productos.domain.exception.SkuDuplicadoException;
import com.foodstore.domain.productos.domain.model.CategoriaId;
import com.foodstore.domain.productos.domain.model.Precio;
import com.foodstore.domain.productos.domain.model.Producto;
import com.foodstore.domain.productos.domain.model.Sku;
import java.util.Currency;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// No es "final": @Transactional necesita que Spring genere un proxy CGLIB
// (subclase en tiempo de ejecucion), y CGLIB no puede subclasificar una clase final.
@Service
@RequiredArgsConstructor
public class CrearProductoUseCase {

    private final ProductoRepositoryPort productoRepositoryPort;

    @Transactional
    public Producto execute(CrearProductoCommand command) {
        Sku sku = Sku.de(command.sku());
        if (productoRepositoryPort.existePorSku(sku)) {
            throw new SkuDuplicadoException(command.sku());
        }

        Precio precio = Precio.de(command.precioMonto(), Currency.getInstance(command.moneda()));
        CategoriaId categoriaId = CategoriaId.de(command.categoriaId());

        Producto producto = Producto.crear(sku, command.nombre(), command.descripcion(), precio, categoriaId);
        return productoRepositoryPort.guardar(producto);
    }
}
