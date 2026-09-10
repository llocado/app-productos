package com.foodstore.infrastructure.rest;

import com.foodstore.application.usecase.ActualizarProductoUseCase;
import com.foodstore.application.usecase.CrearProductoUseCase;
import com.foodstore.application.usecase.EliminarProductoUseCase;
import com.foodstore.application.usecase.GenerarDescripcionProductoUseCase;
import com.foodstore.application.usecase.ListarProductosUseCase;
import com.foodstore.application.usecase.ObtenerProductoUseCase;
import com.foodstore.domain.productos.domain.model.CriterioPaginacion;
import com.foodstore.domain.productos.domain.model.Pagina;
import com.foodstore.domain.productos.domain.model.Producto;
import com.foodstore.domain.productos.domain.model.ProductoId;
import com.foodstore.domain.productos.domain.model.Sku;
import com.foodstore.infrastructure.rest.dto.GenerarDescripcionRequest;
import com.foodstore.infrastructure.rest.dto.GenerarDescripcionResponse;
import com.foodstore.infrastructure.rest.dto.PaginaResponse;
import com.foodstore.infrastructure.rest.dto.ProductoDtoMapper;
import com.foodstore.infrastructure.rest.dto.ProductoRequest;
import com.foodstore.infrastructure.rest.dto.ProductoResponse;
import com.foodstore.infrastructure.rest.dto.ProductoUpdateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final ActualizarProductoUseCase actualizarProductoUseCase;
    private final EliminarProductoUseCase eliminarProductoUseCase;
    private final GenerarDescripcionProductoUseCase generarDescripcionProductoUseCase;
    private final ProductoDtoMapper productoDtoMapper;

    @PostMapping
    public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody ProductoRequest request) {
        Producto creado = crearProductoUseCase.execute(request);
        ProductoResponse response = productoDtoMapper.toResponse(creado);
        return ResponseEntity.created(URI.create("/api/productos/" + response.id())).body(response);
    }

    @GetMapping
    public ResponseEntity<PaginaResponse<ProductoResponse>> listar(
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        Pagina<Producto> resultado = listarProductosUseCase.execute(CriterioPaginacion.de(pagina, tamano));
        return ResponseEntity.ok(PaginaResponse.de(resultado, productoDtoMapper::toResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductoResponse> obtener(@PathVariable String id) {
        Producto producto = obtenerProductoUseCase.execute(ProductoId.de(id));
        return ResponseEntity.ok(productoDtoMapper.toResponse(producto));
    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<ProductoResponse> obtenerPorSku(@PathVariable String sku) {
        Producto producto = obtenerProductoUseCase.execute(Sku.de(sku));
        return ResponseEntity.ok(productoDtoMapper.toResponse(producto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductoResponse> actualizar(
            @PathVariable String id, @Valid @RequestBody ProductoUpdateRequest request) {
        Producto actualizado = actualizarProductoUseCase.execute(ProductoId.de(id), request);
        return ResponseEntity.ok(productoDtoMapper.toResponse(actualizado));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        eliminarProductoUseCase.execute(ProductoId.de(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/generar-descripcion")
    public ResponseEntity<GenerarDescripcionResponse> generarDescripcion(
            @Valid @RequestBody GenerarDescripcionRequest request) {
        String descripcion = generarDescripcionProductoUseCase.execute(request.nombre(), request.categoria());
        return ResponseEntity.ok(new GenerarDescripcionResponse(descripcion));
    }
}
