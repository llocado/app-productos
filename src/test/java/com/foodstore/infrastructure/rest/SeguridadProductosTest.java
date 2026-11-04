package com.foodstore.infrastructure.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodstore.application.usecase.ActualizarProductoUseCase;
import com.foodstore.application.usecase.CrearProductoUseCase;
import com.foodstore.application.usecase.EliminarProductoUseCase;
import com.foodstore.application.usecase.GenerarDescripcionProductoUseCase;
import com.foodstore.application.usecase.IngresarStockUseCase;
import com.foodstore.application.usecase.ListarProductosUseCase;
import com.foodstore.application.usecase.ObtenerProductoUseCase;
import com.foodstore.domain.productos.domain.model.CriterioPaginacion;
import com.foodstore.domain.productos.domain.model.Pagina;
import com.foodstore.domain.productos.domain.model.ProductoId;
import com.foodstore.infrastructure.rest.dto.ProductoDtoMapper;
import com.foodstore.infrastructure.security.SecurityConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Regla de acceso del catalogo: leer es publico; escribir (crear, actualizar, eliminar, ingresar stock, generar
 * descripcion) exige el rol admin. Aqui NO hay usuario simulado por defecto: cada prueba elige quien llama.
 */
@WebMvcTest(ProductoController.class)
@Import({ProductoDtoMapper.class, SecurityConfig.class})
class SeguridadProductosTest {

    private static final String ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
    private static final String CUERPO = "{}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;
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

    /** Todas las operaciones de escritura, con cuerpo (aunque sea invalido: la seguridad actua antes de validar). */
    private List<MockHttpServletRequestBuilder> escrituras() {
        return List.of(
                post("/api/productos").contentType(MediaType.APPLICATION_JSON).content(CUERPO),
                put("/api/productos/" + ID).contentType(MediaType.APPLICATION_JSON).content(CUERPO),
                delete("/api/productos/" + ID),
                post("/api/productos/" + ID + "/stock/ingresos").contentType(MediaType.APPLICATION_JSON).content(CUERPO),
                post("/api/productos/generar-descripcion").contentType(MediaType.APPLICATION_JSON).content(CUERPO));
    }

    @Test
    void escrituras_DeberianResponder401_SinToken() throws Exception {
        for (MockHttpServletRequestBuilder escritura : escrituras()) {
            mockMvc.perform(escritura).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(crearProductoUseCase, actualizarProductoUseCase, eliminarProductoUseCase,
                ingresarStockUseCase, generarDescripcionProductoUseCase);
    }

    @Test
    void escrituras_DeberianResponder403_ConRolCliente() throws Exception {
        for (MockHttpServletRequestBuilder escritura : escrituras()) {
            mockMvc.perform(escritura.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_cliente"))))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(crearProductoUseCase, actualizarProductoUseCase, eliminarProductoUseCase,
                ingresarStockUseCase, generarDescripcionProductoUseCase);
    }

    @Test
    void escritura_DeberiaPasarALaAplicacion_ConRolAdmin() throws Exception {
        mockMvc.perform(delete("/api/productos/" + ID).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_admin"))))
                .andExpect(status().isNoContent());

        verify(eliminarProductoUseCase).execute(ProductoId.de(ID));
    }

    @Test
    void lecturas_DeberianSerPublicas_SinToken() throws Exception {
        when(listarProductosUseCase.execute(any(CriterioPaginacion.class), any()))
                .thenReturn(Pagina.de(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/productos")).andExpect(status().isOk());
    }

    @Test
    void tokenInvalido_DeberiaResponder401() throws Exception {
        when(jwtDecoder.decode("basura")).thenThrow(new BadJwtException("firma invalida"));

        mockMvc.perform(delete("/api/productos/" + ID).header("Authorization", "Bearer basura"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(eliminarProductoUseCase);
    }

    @Test
    void tokenInvalido_DeberiaResponder401_TambienEnUnaLecturaPublica() throws Exception {
        // Spring valida el token si viene, aunque la ruta sea publica: un token manipulado no se "perdona".
        when(jwtDecoder.decode("basura")).thenThrow(new BadJwtException("firma invalida"));

        mockMvc.perform(get("/api/productos").header("Authorization", "Bearer basura"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rutasFueraDelCatalogo_DeberianDenegarseSiempre() throws Exception {
        mockMvc.perform(get("/api/otra-cosa").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_admin"))))
                .andExpect(status().isForbidden());
    }

    // --- El converter de roles de Keycloak (claim realm_access.roles), que jwt() de las pruebas anteriores se salta ---

    private Jwt tokenConRoles(String... roles) {
        return Jwt.withTokenValue("token-de-prueba").header("alg", "none").subject("usuario-1")
                .claim("realm_access", Map.of("roles", List.of(roles))).build();
    }

    @Test
    void rolDelClaimRealmAccess_AdminPuedeEscribir() throws Exception {
        when(jwtDecoder.decode("token-admin")).thenReturn(tokenConRoles("admin"));

        mockMvc.perform(delete("/api/productos/" + ID).header("Authorization", "Bearer token-admin"))
                .andExpect(status().isNoContent());
    }

    @Test
    void rolDelClaimRealmAccess_ClienteNoPuedeEscribir_YUnTokenSinRolesTampoco() throws Exception {
        when(jwtDecoder.decode("token-cliente")).thenReturn(tokenConRoles("cliente"));
        when(jwtDecoder.decode("token-sin-roles")).thenReturn(
                Jwt.withTokenValue("token-sin-roles").header("alg", "none").subject("usuario-2").build());

        mockMvc.perform(delete("/api/productos/" + ID).header("Authorization", "Bearer token-cliente"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/productos/" + ID).header("Authorization", "Bearer token-sin-roles"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(eliminarProductoUseCase);
    }
}
