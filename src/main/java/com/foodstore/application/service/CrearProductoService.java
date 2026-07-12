package com.foodstore.application.service;

import com.foodstore.application.port.in.CrearProductoCommand;
import com.foodstore.application.port.in.CrearProductoUseCase;
import com.foodstore.domain.productos.application.port.ProductoRepositoryPort;
import com.foodstore.domain.productos.domain.exception.SkuDuplicadoException;
import com.foodstore.domain.productos.domain.model.CategoriaId;
import com.foodstore.domain.productos.domain.model.Precio;
import com.foodstore.domain.productos.domain.model.Producto;
import com.foodstore.domain.productos.domain.model.Sku;
import java.util.Currency;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CrearProductoService implements CrearProductoUseCase {

    private final ProductoRepositoryPort productoRepositoryPort;

    @Override
    public Producto crear(CrearProductoCommand command) {
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
