package com.foodstore.application.usecase;

import com.foodstore.application.port.GeneradorDescripcionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public final class GenerarDescripcionProductoUseCase {

    private final GeneradorDescripcionPort generadorDescripcionPort;

    public String execute(String nombre, String categoria) {
        return generadorDescripcionPort.generar(nombre, categoria);
    }
}
