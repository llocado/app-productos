package com.foodstore.application.usecase;

import com.foodstore.domain.productos.application.port.ProductoRepositoryPort;
import com.foodstore.domain.productos.domain.exception.ProductoNoEncontradoException;
import com.foodstore.domain.productos.domain.model.Producto;
import com.foodstore.domain.productos.domain.model.ProductoId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// No es "final": @Transactional necesita que Spring genere un proxy CGLIB.
@Slf4j
@Service
@RequiredArgsConstructor
public class IngresarStockUseCase {

    private final ProductoRepositoryPort productoRepositoryPort;

    /**
     * Suma unidades al stock de un producto (llegada de mercaderia). Es la unica forma de que un producto tenga
     * stock: el dominio lo crea en cero. Transaccional: la version leida al inicio protege contra un descuento
     * por pago que se confirme a la vez (el segundo en confirmar falla en vez de pisar el cambio del primero).
     *
     * @throws ProductoNoEncontradoException si no existe un producto con ese id.
     */
    @Transactional
    public Producto execute(ProductoId id, int unidades) {
        Producto producto = productoRepositoryPort.buscarPorId(id)
                .orElseThrow(() -> new ProductoNoEncontradoException("Producto no encontrado: " + id.getValor()));
        producto.aumentarStock(unidades);
        Producto guardado = productoRepositoryPort.guardar(producto);
        log.info("Stock ingresado productoId={} unidades={} stock={}", id.getValor(), unidades, guardado.getStock().getCantidad());
        return guardado;
    }
}
