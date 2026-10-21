package com.foodstore.infrastructure.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodstore.application.usecase.ActualizarProductoUseCase;
import com.foodstore.application.usecase.CrearProductoUseCase;
import com.foodstore.application.usecase.EliminarProductoUseCase;
import com.foodstore.application.usecase.GenerarDescripcionProductoUseCase;
import com.foodstore.application.usecase.IngresarStockUseCase;
import com.foodstore.application.usecase.ListarProductosUseCase;
import com.foodstore.application.usecase.ObtenerProductoUseCase;
import com.foodstore.infrastructure.config.CorsConfig;
import com.foodstore.infrastructure.rest.dto.ProductoDtoMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** El navegador pregunta con OPTIONS (preflight) antes de leer: solo el origen configurado y solo lectura. */
@WebMvcTest(ProductoController.class)
@Import({ProductoDtoMapper.class, CorsConfig.class})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class CorsConfigTest {

    private static final String ORIGEN_PERMITIDO = "http://localhost:5173";
    private static final String ORIGEN_AJENO = "http://sitio-malicioso.example";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CrearProductoUseCase crearProductoUseCase;
    @MockitoBean
    private ListarProductosUseCase listarProductosUseCase;
    @MockitoBean
    private ObtenerProductoUseCase obtenerProductoUseCase;
    @MockitoBean
    private ActualizarProductoUseCase actualizarProductoUseCase;
    @MockitoBean
    private EliminarProductoUseCase eliminarProductoUseCase;
    @MockitoBean
    private GenerarDescripcionProductoUseCase generarDescripcionProductoUseCase;
    @MockitoBean
    private IngresarStockUseCase ingresarStockUseCase;

    @Test
    void preflightGet_DeberiaAutorizarElOrigenConfigurado() throws Exception {
        mockMvc.perform(options("/api/productos")
                        .header("Origin", ORIGEN_PERMITIDO)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGEN_PERMITIDO))
                .andExpect(header().string("Access-Control-Max-Age", "3600"));
    }

    @Test
    void preflight_DeberiaRechazarUnOrigenAjeno() throws Exception {
        mockMvc.perform(options("/api/productos")
                        .header("Origin", ORIGEN_AJENO)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void preflight_NoDeberiaAutorizarEscrituras_DesdeElNavegador() throws Exception {
        for (String metodo : new String[] {"POST", "PUT", "DELETE"}) {
            mockMvc.perform(options("/api/productos")
                            .header("Origin", ORIGEN_PERMITIDO)
                            .header("Access-Control-Request-Method", metodo))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }

    @Test
    void get_NoDeberiaDevolverCabeceraCors_AUnOrigenAjeno() throws Exception {
        mockMvc.perform(get("/api/productos").header("Origin", ORIGEN_AJENO))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
