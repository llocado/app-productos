package com.foodstore.infrastructure.persistence.repository;

import com.foodstore.infrastructure.persistence.entity.ProductoEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataProductoRepository extends JpaRepository<ProductoEntity, UUID> {

    Optional<ProductoEntity> findBySku(String sku);

    boolean existsBySku(String sku);
}
