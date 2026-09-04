package com.foodstore.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodstore.application.port.GeneradorDescripcionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GenerarDescripcionProductoUseCaseTest {

    @Mock
    private GeneradorDescripcionPort generadorDescripcionPort;

    private GenerarDescripcionProductoUseCase generarDescripcionProductoUseCase;

    @BeforeEach
    void setUp() {
        generarDescripcionProductoUseCase = new GenerarDescripcionProductoUseCase(generadorDescripcionPort);
    }

    @Test
    void execute_DeberiaDelegarEnElPuertoYRetornarLaDescripcion() {
        when(generadorDescripcionPort.generar("Manzana Fuji", "Frutas"))
                .thenReturn("Manzana fresca y crujiente.");

        String resultado = generarDescripcionProductoUseCase.execute("Manzana Fuji", "Frutas");

        assertThat(resultado).isEqualTo("Manzana fresca y crujiente.");
        verify(generadorDescripcionPort).generar("Manzana Fuji", "Frutas");
    }
}
