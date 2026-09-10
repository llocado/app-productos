package com.foodstore.infrastructure.rest.dto;

import com.foodstore.domain.productos.domain.model.Pagina;
import java.util.List;
import java.util.function.Function;

public record PaginaResponse<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas
) {

    public static <E, T> PaginaResponse<T> de(Pagina<E> pagina, Function<E, T> mapper) {
        return new PaginaResponse<>(
                pagina.getContenido().stream().map(mapper).toList(),
                pagina.getNumero(),
                pagina.getTamano(),
                pagina.getTotalElementos(),
                pagina.getTotalPaginas()
        );
    }
}
