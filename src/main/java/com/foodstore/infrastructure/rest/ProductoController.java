package com.foodstore.infrastructure.rest;

import com.foodstore.application.usecase.CrearProductoUseCase;
import com.foodstore.application.usecase.ListarProductosUseCase;
import com.foodstore.application.usecase.ObtenerProductoUseCase;
import com.foodstore.domain.productos.domain.model.Producto;
import com.foodstore.domain.productos.domain.model.ProductoId;
import com.foodstore.infrastructure.rest.dto.ProductoDtoMapper;
import com.foodstore.infrastructure.rest.dto.ProductoRequest;
import com.foodstore.infrastructure.rest.dto.ProductoResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador de entrada HTTP: traduce peticiones REST a llamadas sobre los casos de uso
 * (locales a esta app). No contiene logica de negocio, solo orquestacion y mapeo DTO <-> dominio.
 */
@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final CrearProductoUseCase crearProductoUseCase;
    private final ListarProductosUseCase listarProductosUseCase;
    private final ObtenerProductoUseCase obtenerProductoUseCase;
    private final ProductoDtoMapper productoDtoMapper;

    @PostMapping
    public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody ProductoRequest request) {
        Producto creado = crearProductoUseCase.execute(productoDtoMapper.toCommand(request));
        ProductoResponse response = productoDtoMapper.toResponse(creado);
        return ResponseEntity.created(URI.create("/api/productos/" + response.id())).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ProductoResponse>> listar() {
        List<ProductoResponse> productos = listarProductosUseCase.execute().stream()
                .map(productoDtoMapper::toResponse)
                .toList();
        return ResponseEntity.ok(productos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductoResponse> obtener(@PathVariable String id) {
        Producto producto = obtenerProductoUseCase.execute(ProductoId.de(id));
        return ResponseEntity.ok(productoDtoMapper.toResponse(producto));
    }
}
