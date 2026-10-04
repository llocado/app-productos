package com.foodstore.application.usecase;

import com.foodstore.domain.productos.application.port.ProductoRepositoryPort;
import com.foodstore.domain.productos.domain.exception.ProductoNoEncontradoException;
import com.foodstore.domain.productos.domain.model.ProductoId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// No es "final": @Transactional necesita que Spring genere un proxy CGLIB
// (subclase en tiempo de ejecucion), y CGLIB no puede subclasificar una clase final.
@Slf4j
@Service
@RequiredArgsConstructor
public class EliminarProductoUseCase {

    private final ProductoRepositoryPort productoRepositoryPort;

    /**
     * @throws ProductoNoEncontradoException si no existe un producto con ese id.
     */
    @Transactional
    public void execute(ProductoId id) {
        productoRepositoryPort.buscarPorId(id)
                .orElseThrow(() -> new ProductoNoEncontradoException("Producto no encontrado: " + id.getValor()));

        productoRepositoryPort.eliminar(id);
        log.info("Producto eliminado id={}", id.getValor());
    }
}
