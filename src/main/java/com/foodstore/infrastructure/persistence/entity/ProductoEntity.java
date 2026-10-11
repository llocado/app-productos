package com.foodstore.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "productos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductoEntity {

    // Sin @GeneratedValue: el agregado Producto genera su propio ProductoId
    // (Producto.crear() -> ProductoId.nuevo()), la entidad solo lo persiste.
    @Id
    private UUID id;

    /**
     * Bloqueo optimista: el UPDATE lleva "where version = ?". Si otra transaccion modifico el producto (por ejemplo
     * otro pago descontando el mismo stock) despues de que esta lo leyo, falla con OptimisticLockingFailureException
     * en vez de pisar el cambio. Es Long (no long) para que una entidad nueva (version nula) se inserte con persist.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, unique = true, length = 50)
    private String sku;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 1000)
    private String descripcion;

    @Column(name = "precio_monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioMonto;

    @Column(name = "precio_moneda", nullable = false, length = 3)
    private String precioMoneda;

    @Column(nullable = false)
    private Integer stock;

    @Column(name = "categoria_id")
    private UUID categoriaId;

    @Column(nullable = false)
    private boolean activo;
}
