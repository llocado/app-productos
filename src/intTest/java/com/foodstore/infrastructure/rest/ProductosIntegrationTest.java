package com.foodstore.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodstore.domain.productos.application.port.ProductoRepositoryPort;
import com.foodstore.domain.productos.domain.model.CategoriaId;
import com.foodstore.domain.productos.domain.model.Precio;
import com.foodstore.domain.productos.domain.model.Producto;
import com.foodstore.domain.productos.domain.model.ProductoId;
import com.foodstore.domain.productos.domain.model.Sku;
import com.foodstore.infrastructure.rest.dto.PaginaResponse;
import com.foodstore.infrastructure.rest.dto.ProductoRequest;
import com.foodstore.infrastructure.rest.dto.ProductoResponse;
import com.foodstore.infrastructure.rest.dto.ProductoUpdateRequest;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * Suite de integracion end-to-end (HTTP -> use case -> adaptador JPA) contra un
 * PostgreSQL real gestionado por Testcontainers.
 *
 * @ServiceConnection enlaza dinamicamente el datasource del contenedor sin tocar
 * application.yml. La clase es @Transactional: MockMvc corre en el mismo hilo/
 * transaccion que gestiona el test, asi que todo lo escrito (via HTTP o via
 * ProductoRepositoryPort directamente) se ve dentro del mismo test y se revierte
 * automaticamente al terminar, dejando el contenedor limpio para el siguiente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class ProductosIntegrationTest {

    private static final String CATEGORIA_ID = "b6f3c1e0-2a3b-4c5d-8e9f-0a1b2c3d4e5f";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductoRepositoryPort productoRepositoryPort;

    // ---------------------------------------------------------------
    // Test 1: Crear producto (POST) -> 201 + persistencia real en BD
    // ---------------------------------------------------------------
    @Test
    void crear_DeberiaRetornar201YPersistirEnPostgres_CuandoElRequestEsValido() throws Exception {
        ProductoRequest request = new ProductoRequest(
                "SKU-INT-001",
                "Manzana Fuji",
                "Manzana fresca importada",
                new BigDecimal("1500"),
                "CLP",
                CATEGORIA_ID
        );

        MvcResult result = mockMvc.perform(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.sku").value("SKU-INT-001"))
                .andExpect(jsonPath("$.nombre").value("Manzana Fuji"))
                .andExpect(jsonPath("$.descripcion").value("Manzana fresca importada"))
                .andExpect(jsonPath("$.precioMonto").value(1500))
                .andExpect(jsonPath("$.moneda").value("CLP"))
                .andExpect(jsonPath("$.stock").value(0))
                .andExpect(jsonPath("$.categoriaId").value(CATEGORIA_ID))
                .andExpect(jsonPath("$.activo").value(true))
                .andReturn();

        ProductoResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ProductoResponse.class);

        // Assert extra: se confirma contra el puerto de dominio que el producto
        // quedo realmente persistido en el PostgreSQL del contenedor.
        Optional<Producto> guardado = productoRepositoryPort.buscarPorId(ProductoId.de(response.id()));

        assertThat(guardado).isPresent();
        assertThat(guardado.get().getSku().getValor()).isEqualTo("SKU-INT-001");
        assertThat(guardado.get().getNombre()).isEqualTo("Manzana Fuji");
        assertThat(guardado.get().getPrecio().getMonto()).isEqualByComparingTo(new BigDecimal("1500"));
        assertThat(guardado.get().getPrecio().getMoneda().getCurrencyCode()).isEqualTo("CLP");
        assertThat(guardado.get().getCategoriaId().getValor().toString()).isEqualTo(CATEGORIA_ID);
        assertThat(guardado.get().isActivo()).isTrue();
    }

    // ---------------------------------------------------------------
    // Test 2: Listar productos (GET) -> 200 + lista correcta
    // ---------------------------------------------------------------
    @Test
    void listar_DeberiaRetornar200YLosProductosPersistidos_CuandoExistenProductos() throws Exception {
        Producto manzana = productoRepositoryPort.guardar(crearProducto("SKU-INT-101", "Manzana Fuji", "1500", "CLP"));
        Producto pera = productoRepositoryPort.guardar(crearProducto("SKU-INT-102", "Pera Williams", "1200", "CLP"));

        MvcResult result = mockMvc.perform(get("/api/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(20))
                .andReturn();

        PaginaResponse<ProductoResponse> pagina = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructParametricType(PaginaResponse.class, ProductoResponse.class));
        List<ProductoResponse> productos = pagina.contenido();

        assertThat(productos).hasSize(2);
        assertThat(productos)
                .filteredOn(p -> p.sku().equals("SKU-INT-101"))
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.id()).isEqualTo(manzana.getId().getValor().toString());
                    assertThat(p.nombre()).isEqualTo("Manzana Fuji");
                });
        assertThat(productos)
                .filteredOn(p -> p.sku().equals("SKU-INT-102"))
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.id()).isEqualTo(pera.getId().getValor().toString());
                    assertThat(p.nombre()).isEqualTo("Pera Williams");
                });
    }

    // ---------------------------------------------------------------
    // Test 3: Obtener por SKU (GET /sku/{sku}) -> 200 feliz / 404 alternativo
    // ---------------------------------------------------------------
    @Test
    void obtenerPorSku_DeberiaRetornar200YElProducto_CuandoElSkuExiste() throws Exception {
        Producto guardado = productoRepositoryPort.guardar(
                crearProducto("SKU-INT-201", "Platano Cavendish", "900", "CLP"));

        mockMvc.perform(get("/api/productos/sku/{sku}", "SKU-INT-201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(guardado.getId().getValor().toString()))
                .andExpect(jsonPath("$.sku").value("SKU-INT-201"))
                .andExpect(jsonPath("$.nombre").value("Platano Cavendish"))
                .andExpect(jsonPath("$.precioMonto").value(900))
                .andExpect(jsonPath("$.moneda").value("CLP"));
    }

    @Test
    void obtenerPorSku_DeberiaRetornar404_CuandoElSkuNoExiste() throws Exception {
        mockMvc.perform(get("/api/productos/sku/{sku}", "SKU-NO-EXISTE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    // ---------------------------------------------------------------
    // Test 4: Actualizar producto (PUT) -> 200 + cambios persistidos en BD
    // ---------------------------------------------------------------
    @Test
    void actualizar_DeberiaRetornar200YPersistirLosCambiosEnPostgres_CuandoElRequestEsValido() throws Exception {
        Producto guardado = productoRepositoryPort.guardar(
                crearProducto("SKU-INT-301", "Manzana Fuji", "1500", "CLP"));
        String id = guardado.getId().getValor().toString();

        ProductoUpdateRequest request = new ProductoUpdateRequest(
                "Manzana Fuji Premium",
                "Manzana fresca importada, calibre extra",
                new BigDecimal("1800"),
                "CLP",
                CATEGORIA_ID
        );

        mockMvc.perform(put("/api/productos/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Manzana Fuji Premium"))
                .andExpect(jsonPath("$.precioMonto").value(1800));

        Optional<Producto> actualizado = productoRepositoryPort.buscarPorId(guardado.getId());

        assertThat(actualizado).isPresent();
        assertThat(actualizado.get().getNombre()).isEqualTo("Manzana Fuji Premium");
        assertThat(actualizado.get().getDescripcion()).isEqualTo("Manzana fresca importada, calibre extra");
        assertThat(actualizado.get().getPrecio().getMonto()).isEqualByComparingTo(new BigDecimal("1800"));
    }

    @Test
    void actualizar_DeberiaRetornar404_CuandoElProductoNoExiste() throws Exception {
        String idInexistente = UUID.randomUUID().toString();
        ProductoUpdateRequest request = new ProductoUpdateRequest(
                "Manzana Fuji Premium",
                "Manzana fresca importada",
                new BigDecimal("1800"),
                "CLP",
                CATEGORIA_ID
        );

        mockMvc.perform(put("/api/productos/{id}", idInexistente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    // ---------------------------------------------------------------
    // Test 5: Eliminar producto (DELETE) -> 204 + ya no existe en BD
    // ---------------------------------------------------------------
    @Test
    void eliminar_DeberiaRetornar204YBorrarDePostgres_CuandoElProductoExiste() throws Exception {
        Producto guardado = productoRepositoryPort.guardar(
                crearProducto("SKU-INT-401", "Pan Baguette", "1200", "CLP"));
        String id = guardado.getId().getValor().toString();

        mockMvc.perform(delete("/api/productos/{id}", id))
                .andExpect(status().isNoContent());

        assertThat(productoRepositoryPort.buscarPorId(guardado.getId())).isEmpty();
    }

    @Test
    void eliminar_DeberiaRetornar404_CuandoElProductoNoExiste() throws Exception {
        String idInexistente = UUID.randomUUID().toString();

        mockMvc.perform(delete("/api/productos/{id}", idInexistente))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    private static Producto crearProducto(String sku, String nombre, String monto, String moneda) {
        return Producto.crear(
                Sku.de(sku),
                nombre,
                nombre + " - descripcion de prueba",
                Precio.de(new BigDecimal(monto), Currency.getInstance(moneda)),
                CategoriaId.de(CATEGORIA_ID)
        );
    }
}
