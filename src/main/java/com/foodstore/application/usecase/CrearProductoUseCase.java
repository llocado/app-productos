package com.foodstore.application.usecase;

import com.foodstore.domain.productos.application.port.ProductoRepositoryPort;
import com.foodstore.domain.productos.domain.exception.SkuDuplicadoException;
import com.foodstore.domain.productos.domain.model.CategoriaId;
import com.foodstore.domain.productos.domain.model.Precio;
import com.foodstore.domain.productos.domain.model.Producto;
import com.foodstore.domain.productos.domain.model.Sku;
import com.foodstore.infrastructure.rest.dto.ProductoRequest;
import java.util.Currency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// No es "final": @Transactional necesita que Spring genere un proxy CGLIB
// (subclase en tiempo de ejecucion), y CGLIB no puede subclasificar una clase final.
@Slf4j
@Service
@RequiredArgsConstructor
public class CrearProductoUseCase {

    private final ProductoRepositoryPort productoRepositoryPort;

    @Transactional
    public Producto execute(ProductoRequest request) {
        Sku sku = Sku.de(request.sku());
        if (productoRepositoryPort.existePorSku(sku)) {
            throw new SkuDuplicadoException(request.sku());
        }

        Precio precio = Precio.de(request.precioMonto(), Currency.getInstance(request.moneda()));
        CategoriaId categoriaId = CategoriaId.de(request.categoriaId());

        Producto producto = Producto.crear(sku, request.nombre(), request.descripcion(), precio, categoriaId);
        Producto guardado = productoRepositoryPort.guardar(producto);
        log.info("Producto creado id={} sku={}", guardado.getId().getValor(), request.sku());
        return guardado;
    }
}
