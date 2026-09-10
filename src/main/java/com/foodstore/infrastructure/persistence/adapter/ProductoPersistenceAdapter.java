package com.foodstore.infrastructure.persistence.adapter;

import com.foodstore.domain.productos.application.port.ProductoRepositoryPort;
import com.foodstore.domain.productos.domain.model.CriterioPaginacion;
import com.foodstore.domain.productos.domain.model.Pagina;
import com.foodstore.domain.productos.domain.model.Producto;
import com.foodstore.domain.productos.domain.model.ProductoId;
import com.foodstore.domain.productos.domain.model.Sku;
import com.foodstore.infrastructure.persistence.entity.ProductoEntity;
import com.foodstore.infrastructure.persistence.mapper.ProductoEntityMapper;
import com.foodstore.infrastructure.persistence.repository.SpringDataProductoRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Adaptador de salida: implementa el puerto del dominio usando Spring Data JPA.
 * Es el unico punto de la app que conoce tanto el modelo de dominio como la entidad JPA.
 * Tambien es el unico punto que traduce entre la paginacion agnostica del
 * dominio (CriterioPaginacion / Pagina) y el Pageable/Page de Spring Data.
 */
@Component
@RequiredArgsConstructor
public class ProductoPersistenceAdapter implements ProductoRepositoryPort {

    private final SpringDataProductoRepository springDataProductoRepository;
    private final ProductoEntityMapper productoEntityMapper;

    @Override
    public Producto guardar(Producto producto) {
        ProductoEntity entity = productoEntityMapper.toEntity(producto);
        ProductoEntity guardado = springDataProductoRepository.save(entity);
        return productoEntityMapper.toDomain(guardado);
    }

    @Override
    public Optional<Producto> buscarPorId(ProductoId id) {
        return springDataProductoRepository.findById(id.getValor())
                .map(productoEntityMapper::toDomain);
    }

    @Override
    public Optional<Producto> buscarPorSku(Sku sku) {
        return springDataProductoRepository.findBySku(sku.getValor())
                .map(productoEntityMapper::toDomain);
    }

    @Override
    public List<Producto> listarTodos() {
        return springDataProductoRepository.findAll().stream()
                .map(productoEntityMapper::toDomain)
                .toList();
    }

    @Override
    public Pagina<Producto> listarPaginado(CriterioPaginacion criterio) {
        Pageable pageable = PageRequest.of(
                criterio.getPagina(), criterio.getTamano(), Sort.by("nombre").ascending());
        Page<Producto> pagina = springDataProductoRepository.findAll(pageable)
                .map(productoEntityMapper::toDomain);
        return Pagina.de(pagina.getContent(), pagina.getNumber(), pagina.getSize(), pagina.getTotalElements());
    }

    @Override
    public boolean existePorSku(Sku sku) {
        return springDataProductoRepository.existsBySku(sku.getValor());
    }

    @Override
    public void eliminar(ProductoId id) {
        springDataProductoRepository.deleteById(id.getValor());
    }
}
