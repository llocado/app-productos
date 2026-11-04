package com.foodstore.infrastructure.persistence.adapter;

import com.foodstore.application.port.EventoProcesadoPort;
import com.foodstore.infrastructure.persistence.entity.EventoProcesadoEntity;
import com.foodstore.infrastructure.persistence.repository.SpringDataEventoProcesadoRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EventoProcesadoAdapter implements EventoProcesadoPort {

    private final SpringDataEventoProcesadoRepository springDataEventoProcesadoRepository;

    @Override
    public boolean yaFueProcesado(UUID eventId) {
        return springDataEventoProcesadoRepository.existsById(eventId);
    }

    @Override
    public void marcarComoProcesado(UUID eventId) {
        EventoProcesadoEntity entity = new EventoProcesadoEntity();
        entity.setEventId(eventId);
        entity.setProcesadoEn(Instant.now());
        springDataEventoProcesadoRepository.save(entity);
    }

    @Override
    @Transactional
    public int eliminarProcesadosAnterioresA(Instant limite, int lote) {
        return springDataEventoProcesadoRepository.eliminarAnterioresA(limite, lote);
    }
}
