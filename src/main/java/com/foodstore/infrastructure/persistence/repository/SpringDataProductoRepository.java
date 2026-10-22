package com.foodstore.infrastructure.persistence.repository;

import com.foodstore.infrastructure.persistence.entity.ProductoEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataProductoRepository extends JpaRepository<ProductoEntity, UUID> {

    Optional<ProductoEntity> findBySku(String sku);

    boolean existsBySku(String sku);

    // "Containing" arma LIKE '%texto%' y escapa % y _ del texto, e "IgnoreCase" compara en minusculas: asi "lech" encuentra
    // "Leche Entera" y un "%" escrito por el usuario se busca como el caracter, no como comodin.
    Page<ProductoEntity> findByNombreContainingIgnoreCase(String texto, Pageable pageable);
}
