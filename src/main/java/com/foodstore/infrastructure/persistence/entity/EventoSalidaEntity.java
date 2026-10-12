package com.foodstore.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

/** Fila del outbox: el evento (envelope completo en JSON) queda guardado junto al pago hasta que se publica. */
@Entity
@Table(name = "eventos_salida")
@Getter
@Setter
@NoArgsConstructor
public class EventoSalidaEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "routing_key", nullable = false, length = 100)
    private String routingKey;

    /** Id del checkout: el publicador lo usa para que sus logs se puedan seguir junto a los del resto del flujo. */
    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "publicado_en")
    private Instant publicadoEn;

    @Column(nullable = false)
    private int intentos;

    /**
     * El id lo asigna el codigo (eventId determinista), asi que sin esto Spring Data no sabe si la fila es nueva y
     * hace merge: un eventId repetido sobrescribiria la fila ya publicada y reenviaria el evento. Con isNew()
     * verdadero hasta guardarse o cargarse, un repetido hace persist y falla por la clave primaria.
     */
    @Transient
    @Setter(lombok.AccessLevel.NONE)
    private boolean nueva = true;

    @Override
    public boolean isNew() {
        return nueva;
    }

    @PostLoad
    @PostPersist
    void marcarComoExistente() {
        this.nueva = false;
    }
}
