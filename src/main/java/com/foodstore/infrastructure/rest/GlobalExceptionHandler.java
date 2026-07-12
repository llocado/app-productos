package com.foodstore.infrastructure.rest;

import com.foodstore.domain.productos.domain.exception.ProductoNoEncontradoException;
import com.foodstore.domain.productos.domain.exception.SkuDuplicadoException;
import com.foodstore.domain.productos.domain.exception.StockInsuficienteException;
import com.foodstore.infrastructure.rest.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleNoEncontrado(ProductoNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(SkuDuplicadoException.class)
    public ResponseEntity<ErrorResponse> handleSkuDuplicado(SkuDuplicadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(StockInsuficienteException.class)
    public ResponseEntity<ErrorResponse> handleStockInsuficiente(StockInsuficienteException ex) {
        String mensaje = "Stock insuficiente: disponible=" + ex.getDisponible() + ", solicitado=" + ex.getSolicitado();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse(mensaje));
    }
}
