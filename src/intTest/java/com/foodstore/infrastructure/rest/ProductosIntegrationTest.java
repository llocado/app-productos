package com.foodstore.infrastructure.rest;


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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
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
// La seguridad real esta activa: estas pruebas actuan como un admin (la regla de acceso se prueba en SeguridadProductosTest).
@MockitoBean(types = JwtDecoder.class)
// Esta suite no levanta RabbitMQ: el listener no arranca y el puerto invalido garantiza que, pase lo que pase,
// nunca se conecte al RabbitMQ de desarrollo (declararia colas ni consumiria eventos reales).
@TestPropertySource(properties = {"spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.port=1"})
class ProductosIntegrationTest {

    private static final String CATEGORIA_ID = "b6f3c1e0-2a3b-4c5d-8e9f-0a1b2c3d4e5f";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    /** Ejecuta la peticion como un admin autenticado (el JWT se simula: no hace falta Keycloak). */
    private ResultActions comoAdmin(MockHttpServletRequestBuilder peticion) throws Exception {
        return mockMvc.perform(peticion.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_admin"))));
    }

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

        MvcResult result = comoAdmin(post("/api/productos")
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

        MvcResult result = comoAdmin(get("/api/productos"))
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

        comoAdmin(get("/api/productos/sku/{sku}", "SKU-INT-201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(guardado.getId().getValor().toString()))
                .andExpect(jsonPath("$.sku").value("SKU-INT-201"))
                .andExpect(jsonPath("$.nombre").value("Platano Cavendish"))
                .andExpect(jsonPath("$.precioMonto").value(900))
                .andExpect(jsonPath("$.moneda").value("CLP"));
    }

    @Test
    void obtenerPorSku_DeberiaRetornar404_CuandoElSkuNoExiste() throws Exception {
        comoAdmin(get("/api/productos/sku/{sku}", "SKU-NO-EXISTE"))
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

        comoAdmin(put("/api/productos/{id}", id)
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

        comoAdmin(put("/api/productos/{id}", idInexistente)
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

        comoAdmin(delete("/api/productos/{id}", id))
                .andExpect(status().isNoContent());

        assertThat(productoRepositoryPort.buscarPorId(guardado.getId())).isEmpty();
    }

    @Test
    void eliminar_DeberiaRetornar404_CuandoElProductoNoExiste() throws Exception {
        String idInexistente = UUID.randomUUID().toString();

        comoAdmin(delete("/api/productos/{id}", idInexistente))
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

    // ---------------------------------------------------------------
    // Ingreso de stock (POST /{id}/stock/ingresos) -> el stock queda guardado y se suma
    // ---------------------------------------------------------------
    @Test
    void ingresarStock_DeberiaSumarYPersistirElStock_EnPostgres() throws Exception {
        ProductoRequest request = new ProductoRequest(
                "SKU-INT-STOCK", "Leche Entera", "1 litro", new BigDecimal("1100"), "CLP", CATEGORIA_ID);
        MvcResult creado = comoAdmin(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        ProductoResponse producto = objectMapper.readValue(creado.getResponse().getContentAsString(), ProductoResponse.class);

        comoAdmin(post("/api/productos/{id}/stock/ingresos", producto.id())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"unidades\": 25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(25));
        comoAdmin(post("/api/productos/{id}/stock/ingresos", producto.id())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"unidades\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(30));

        comoAdmin(get("/api/productos/{id}", producto.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(30));
    }

    @Test
    void ingresarStock_DeberiaRetornar404_CuandoElProductoNoExiste() throws Exception {
        comoAdmin(post("/api/productos/{id}/stock/ingresos", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"unidades\": 5}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ingresarStock_DeberiaRetornar400_CuandoLasUnidadesSonInvalidas() throws Exception {
        comoAdmin(post("/api/productos/{id}/stock/ingresos", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"unidades\": 0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingresarStock_DeberiaRetornar400YNoCambiarElStock_CuandoLasUnidadesSonFraccionarias() throws Exception {
        ProductoRequest request = new ProductoRequest(
                "SKU-INT-FRAC", "Arroz", "1 kg", new BigDecimal("1500"), "CLP", CATEGORIA_ID);
        MvcResult creado = comoAdmin(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        ProductoResponse producto = objectMapper.readValue(creado.getResponse().getContentAsString(), ProductoResponse.class);

        comoAdmin(post("/api/productos/{id}/stock/ingresos", producto.id())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"unidades\": 1.5}"))
                .andExpect(status().isBadRequest());

        comoAdmin(get("/api/productos/{id}", producto.id()))
                .andExpect(jsonPath("$.stock").value(0));
    }

    // ---------------------------------------------------------------
    // Busqueda por nombre (GET /api/productos?nombre=) contra Postgres real
    // ---------------------------------------------------------------
    @Test
    void listar_DeberiaBuscarPorNombreParcialYSinDistinguirMayusculas() throws Exception {
        crearProductoParaBusqueda("SKU-BUS-1", "Leche Entera");
        crearProductoParaBusqueda("SKU-BUS-2", "Leche Descremada");
        crearProductoParaBusqueda("SKU-BUS-3", "Manzana Fuji");

        comoAdmin(get("/api/productos").param("nombre", "LECH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Leche Descremada"))
                .andExpect(jsonPath("$.contenido[1].nombre").value("Leche Entera"));
        comoAdmin(get("/api/productos").param("nombre", "zana f"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].sku").value("SKU-BUS-3"));
        comoAdmin(get("/api/productos").param("nombre", "no-existe-nada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.contenido").isEmpty());
    }

    @Test
    void listar_DeberiaTomarLosComodinesDeLikeComoTextoLiteral() throws Exception {
        crearProductoParaBusqueda("SKU-LIT-1", "Oferta 50% descuento");
        crearProductoParaBusqueda("SKU-LIT-2", "Oferta 50 unidades");

        // "%" suelto como comodin encontraria los dos; como texto literal solo el que lo trae escrito.
        comoAdmin(get("/api/productos").param("nombre", "50%"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].sku").value("SKU-LIT-1"));
        comoAdmin(get("/api/productos").param("nombre", "Oferta_50"))
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void listar_DeberiaBuscarSinDistinguirTildes_EnAmbosSentidos() throws Exception {
        crearProductoParaBusqueda("SKU-TIL-1", "Plátano");
        crearProductoParaBusqueda("SKU-TIL-2", "Jamón Serrano");
        crearProductoParaBusqueda("SKU-TIL-3", "Cafe Molido");

        comoAdmin(get("/api/productos").param("nombre", "platano"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].sku").value("SKU-TIL-1"));
        comoAdmin(get("/api/productos").param("nombre", "JAMON"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].sku").value("SKU-TIL-2"));
        // Al reves: el usuario escribe la tilde y el nombre guardado no la lleva.
        comoAdmin(get("/api/productos").param("nombre", "café"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].sku").value("SKU-TIL-3"));
    }

    @Test
    void listar_DeberiaTomarLaBarraInversaComoTextoLiteral() throws Exception {
        crearProductoParaBusqueda("SKU-BAR-1", "Ruta C:\\tmp");
        crearProductoParaBusqueda("SKU-BAR-2", "Ruta C tmp");

        comoAdmin(get("/api/productos").param("nombre", "C:\\"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].sku").value("SKU-BAR-1"));
    }

    @Test
    void listar_DeberiaListarTodo_CuandoElNombreVieneEnBlanco() throws Exception {
        crearProductoParaBusqueda("SKU-BLA-1", "Pan Molde");

        comoAdmin(get("/api/productos").param("nombre", "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    private void crearProductoParaBusqueda(String sku, String nombre) throws Exception {
        ProductoRequest request = new ProductoRequest(sku, nombre, "Descripcion", new BigDecimal("1000"), "CLP", CATEGORIA_ID);
        comoAdmin(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}
