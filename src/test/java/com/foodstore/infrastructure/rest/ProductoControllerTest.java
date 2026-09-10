package com.foodstore.infrastructure.rest;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodstore.application.usecase.ActualizarProductoUseCase;
import com.foodstore.application.usecase.CrearProductoUseCase;
import com.foodstore.application.usecase.EliminarProductoUseCase;
import com.foodstore.application.usecase.GenerarDescripcionProductoUseCase;
import com.foodstore.application.usecase.ListarProductosUseCase;
import com.foodstore.application.usecase.ObtenerProductoUseCase;
import com.foodstore.domain.productos.domain.exception.ProductoNoEncontradoException;
import com.foodstore.domain.productos.domain.model.CategoriaId;
import com.foodstore.domain.productos.domain.model.CriterioPaginacion;
import com.foodstore.domain.productos.domain.model.Pagina;
import com.foodstore.domain.productos.domain.model.Precio;
import com.foodstore.domain.productos.domain.model.Producto;
import com.foodstore.domain.productos.domain.model.ProductoId;
import com.foodstore.domain.productos.domain.model.Sku;
import com.foodstore.infrastructure.rest.dto.GenerarDescripcionRequest;
import com.foodstore.infrastructure.rest.dto.ProductoDtoMapper;
import com.foodstore.infrastructure.rest.dto.ProductoRequest;
import com.foodstore.infrastructure.rest.dto.ProductoUpdateRequest;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Slice de presentacion: solo levanta el MVC de ProductoController (mas el
 * GlobalExceptionHandler, autodetectado por @WebMvcTest). Los casos de uso se
 * mockean con @MockitoBean (reemplazo de @MockBean, retirado en Spring Boot 4),
 * por lo que no se toca la base de datos.
 */
@WebMvcTest(ProductoController.class)
@Import(ProductoDtoMapper.class)
class ProductoControllerTest {

    private static final String CATEGORIA_ID = "b6f3c1e0-2a3b-4c5d-8e9f-0a1b2c3d4e5f";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    void crear_DeberiaRetornar201YElProductoCreado_CuandoElRequestEsValido() throws Exception {
        ProductoRequest request = new ProductoRequest(
                "SKU-001",
                "Manzana Fuji",
                "Manzana fresca importada",
                new BigDecimal("1500"),
                "CLP",
                CATEGORIA_ID
        );
        Producto creado = crearProducto();
        String idEsperado = creado.getId().getValor().toString();

        when(crearProductoUseCase.execute(any(ProductoRequest.class))).thenReturn(creado);

        mockMvc.perform(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/productos/" + idEsperado))
                .andExpect(jsonPath("$.id").value(idEsperado))
                .andExpect(jsonPath("$.sku").value("SKU-001"))
                .andExpect(jsonPath("$.nombre").value("Manzana Fuji"))
                .andExpect(jsonPath("$.descripcion").value("Manzana fresca importada"))
                .andExpect(jsonPath("$.precioMonto").value(1500))
                .andExpect(jsonPath("$.moneda").value("CLP"))
                .andExpect(jsonPath("$.stock").value(0))
                .andExpect(jsonPath("$.categoriaId").value(CATEGORIA_ID))
                .andExpect(jsonPath("$.activo").value(true));

        verify(crearProductoUseCase).execute(any(ProductoRequest.class));
    }

    @Test
    void crear_DeberiaRetornar400_CuandoElRequestEsInvalido() throws Exception {
        ProductoRequest requestInvalido = new ProductoRequest(
                "SKU-001",
                "",
                "Manzana fresca importada",
                new BigDecimal("-10"),
                "CLP",
                CATEGORIA_ID
        );

        mockMvc.perform(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest());

        verify(crearProductoUseCase, never()).execute(any(ProductoRequest.class));
    }

    @Test
    void listar_DeberiaRetornar200YLaPaginaDeProductos_ConLosParametrosPorDefecto() throws Exception {
        Producto producto = crearProducto();
        Pagina<Producto> pagina = Pagina.de(List.of(producto), 0, 20, 1);

        when(listarProductosUseCase.execute(any(CriterioPaginacion.class))).thenReturn(pagina);

        mockMvc.perform(get("/api/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].sku").value("SKU-001"))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(20))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(1));

        verify(listarProductosUseCase).execute(any(CriterioPaginacion.class));
    }

    @Test
    void listar_DeberiaRetornar400_CuandoElTamanoExcedeElMaximoPermitido() throws Exception {
        mockMvc.perform(get("/api/productos").param("tamano", "101"))
                .andExpect(status().isBadRequest());

        verify(listarProductosUseCase, never()).execute(any(CriterioPaginacion.class));
    }

    @Test
    void obtener_DeberiaRetornar200YElProducto_CuandoElProductoExiste() throws Exception {
        Producto producto = crearProducto();
        String id = producto.getId().getValor().toString();

        when(obtenerProductoUseCase.execute(eq(producto.getId()))).thenReturn(producto);

        mockMvc.perform(get("/api/productos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.sku").value("SKU-001"))
                .andExpect(jsonPath("$.nombre").value("Manzana Fuji"));

        verify(obtenerProductoUseCase).execute(eq(producto.getId()));
    }

    @Test
    void obtener_DeberiaRetornar404_CuandoElProductoNoExiste() throws Exception {
        String idInexistente = UUID.randomUUID().toString();
        String mensaje = "Producto no encontrado: " + idInexistente;

        when(obtenerProductoUseCase.execute(any(ProductoId.class)))
                .thenThrow(new ProductoNoEncontradoException(mensaje));

        mockMvc.perform(get("/api/productos/{id}", idInexistente))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(mensaje));
    }

    @Test
    void actualizar_DeberiaRetornar200YElProductoActualizado_CuandoElRequestEsValido() throws Exception {
        Producto producto = crearProducto();
        String id = producto.getId().getValor().toString();
        ProductoUpdateRequest request = new ProductoUpdateRequest(
                "Manzana Fuji Premium",
                "Manzana fresca importada, calibre extra",
                new BigDecimal("1800"),
                "CLP",
                CATEGORIA_ID
        );

        when(actualizarProductoUseCase.execute(eq(producto.getId()), any(ProductoUpdateRequest.class)))
                .thenReturn(producto);

        mockMvc.perform(put("/api/productos/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        verify(actualizarProductoUseCase).execute(eq(producto.getId()), any(ProductoUpdateRequest.class));
    }

    @Test
    void actualizar_DeberiaRetornar400_CuandoElRequestEsInvalido() throws Exception {
        String id = UUID.randomUUID().toString();
        ProductoUpdateRequest requestInvalido = new ProductoUpdateRequest(
                "",
                "Manzana fresca importada",
                new BigDecimal("-10"),
                "CLP",
                CATEGORIA_ID
        );

        mockMvc.perform(put("/api/productos/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest());

        verify(actualizarProductoUseCase, never()).execute(any(ProductoId.class), any(ProductoUpdateRequest.class));
    }

    @Test
    void actualizar_DeberiaRetornar404_CuandoElProductoNoExiste() throws Exception {
        String idInexistente = UUID.randomUUID().toString();
        String mensaje = "Producto no encontrado: " + idInexistente;
        ProductoUpdateRequest request = new ProductoUpdateRequest(
                "Manzana Fuji Premium",
                "Manzana fresca importada",
                new BigDecimal("1800"),
                "CLP",
                CATEGORIA_ID
        );

        when(actualizarProductoUseCase.execute(any(ProductoId.class), any(ProductoUpdateRequest.class)))
                .thenThrow(new ProductoNoEncontradoException(mensaje));

        mockMvc.perform(put("/api/productos/{id}", idInexistente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(mensaje));
    }

    @Test
    void eliminar_DeberiaRetornar204_CuandoElProductoExiste() throws Exception {
        String id = UUID.randomUUID().toString();

        mockMvc.perform(delete("/api/productos/{id}", id))
                .andExpect(status().isNoContent());

        verify(eliminarProductoUseCase).execute(eq(ProductoId.de(id)));
    }

    @Test
    void eliminar_DeberiaRetornar404_CuandoElProductoNoExiste() throws Exception {
        String idInexistente = UUID.randomUUID().toString();
        String mensaje = "Producto no encontrado: " + idInexistente;

        doThrow(new ProductoNoEncontradoException(mensaje))
                .when(eliminarProductoUseCase).execute(any(ProductoId.class));

        mockMvc.perform(delete("/api/productos/{id}", idInexistente))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(mensaje));
    }

    @Test
    void generarDescripcion_DeberiaRetornar200YLaDescripcion_CuandoElRequestEsValido() throws Exception {
        GenerarDescripcionRequest request = new GenerarDescripcionRequest("Manzana Fuji", "Frutas");

        when(generarDescripcionProductoUseCase.execute("Manzana Fuji", "Frutas"))
                .thenReturn("Manzana fresca y crujiente, ideal para el dia a dia.");

        mockMvc.perform(post("/api/productos/generar-descripcion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descripcion").value("Manzana fresca y crujiente, ideal para el dia a dia."));

        verify(generarDescripcionProductoUseCase).execute("Manzana Fuji", "Frutas");
    }

    @Test
    void generarDescripcion_DeberiaRetornar400_CuandoElNombreEstaVacio() throws Exception {
        GenerarDescripcionRequest requestInvalido = new GenerarDescripcionRequest("", "Frutas");

        mockMvc.perform(post("/api/productos/generar-descripcion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest());

        verify(generarDescripcionProductoUseCase, never()).execute(any(), any());
    }

    private Producto crearProducto() {
        Sku sku = Sku.de("SKU-001");
        Precio precio = Precio.de(new BigDecimal("1500"), Currency.getInstance("CLP"));
        CategoriaId categoriaId = CategoriaId.de(CATEGORIA_ID);
        return Producto.crear(sku, "Manzana Fuji", "Manzana fresca importada", precio, categoriaId);
    }
}
