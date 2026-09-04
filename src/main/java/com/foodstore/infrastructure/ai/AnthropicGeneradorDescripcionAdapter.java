package com.foodstore.infrastructure.ai;

import com.foodstore.application.port.GeneradorDescripcionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Adaptador de salida: implementa el puerto de generacion de descripciones
 * usando Spring AI (ChatClient) contra un modelo Claude. Es el unico punto
 * de la app que conoce Spring AI; el caso de uso solo ve GeneradorDescripcionPort.
 */
@Component
@RequiredArgsConstructor
public class AnthropicGeneradorDescripcionAdapter implements GeneradorDescripcionPort {

    private static final String PROMPT = """
            Escribe una descripcion de producto breve (2 a 3 frases), en espanol,
            para el catalogo de una tienda de alimentos.
            Producto: %s
            Categoria: %s
            No inventes datos nutricionales, precios ni promociones.
            Responde unicamente con la descripcion, sin comillas ni prefijos.
            """;

    private final ChatClient chatClient;

    @Override
    public String generar(String nombre, String categoria) {
        String prompt = PROMPT.formatted(nombre, categoria != null ? categoria : "sin especificar");
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
}
