package com.foodstore.application.port;

/**
 * Puerto de salida hacia un generador de texto (IA) para redactar descripciones
 * de producto. La implementacion concreta (Spring AI + Claude, u otro proveedor)
 * vive en infrastructure; el caso de uso solo conoce este contrato.
 */
public interface GeneradorDescripcionPort {

    String generar(String nombre, String categoria);
}
