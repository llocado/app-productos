package com.foodstore.infrastructure.persistence.repository;

import com.foodstore.infrastructure.persistence.entity.ProductoEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataProductoRepository extends JpaRepository<ProductoEntity, UUID> {

    Optional<ProductoEntity> findBySku(String sku);

    boolean existsBySku(String sku);

    // Compara sin mayusculas ni tildes: unaccent (extension de Postgres, ver el changelog 004) a ambos lados. "function" llama
    // a una funcion de la base que JPQL no conoce; el cast le dice a Hibernate que devuelve texto. El patron ya trae los % y el escape de los comodines (ver el adaptador).
    @Query("select p from ProductoEntity p where cast(function('unaccent', lower(p.nombre)) as string) like cast(function('unaccent', lower(:patron)) as string) escape '\\'")
    Page<ProductoEntity> buscarPorNombreSinTildes(@Param("patron") String patron, Pageable pageable);
}
