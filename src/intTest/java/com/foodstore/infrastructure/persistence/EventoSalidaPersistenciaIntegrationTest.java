package com.foodstore.infrastructure.persistence;

import com.foodstore.application.port.EventoSalidaPort;
import com.foodstore.infrastructure.persistence.entity.EventoSalidaEntity;
import com.foodstore.infrastructure.persistence.repository.SpringDataEventoSalidaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El eventId del outbox es determinista y lo asigna el codigo. Registrar dos veces el mismo evento debe fallar por la
 * clave primaria: con merge (comportamiento por defecto de Spring Data con id asignado) sobrescribiria la fila ya
 * publicada y reenviaria el evento.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {"spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.port=1"})
class EventoSalidaPersistenciaIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private EventoSalidaPort eventoSalidaPort;

    @Autowired
    private SpringDataEventoSalidaRepository repository;

    private EventoSalidaPort.StockDescuentoRechazado evento(UUID pagoId) {
        return new EventoSalidaPort.StockDescuentoRechazado(pagoId, UUID.randomUUID(), "usuario-1",
                List.of(new EventoSalidaPort.Faltante(UUID.randomUUID(), 3, 1)));
    }

    @Test
    void registrarDosVecesElMismoEvento_DeberiaFallarSinSobrescribirLaFilaYaPublicada() {
        UUID pagoId = UUID.randomUUID();
        eventoSalidaPort.registrar(evento(pagoId), UUID.randomUUID());
        EventoSalidaEntity guardada = repository.findAll().stream()
                .filter(e -> e.getPayload().contains(pagoId.toString())).findFirst().orElseThrow();
        Instant publicadoEn = Instant.now();
        guardada.setPublicadoEn(publicadoEn);
        guardada.setIntentos(2);
        repository.saveAndFlush(guardada);

        assertThatThrownBy(() -> eventoSalidaPort.registrar(evento(pagoId), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        EventoSalidaEntity despues = repository.findById(guardada.getId()).orElseThrow();
        assertThat(despues.getPublicadoEn()).isNotNull();
        assertThat(despues.getIntentos()).isEqualTo(2);
    }
}
