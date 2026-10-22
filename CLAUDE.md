# app-productos (productos-service)

Catálogo del proyecto supermercado. Primer servicio construido, el más
maduro de todos. Reglas compartidas con el resto del proyecto en
`../CLAUDE.md` (arquitectura hexagonal, commits, verificación, etc.) —
este archivo solo cubre lo específico de este repo.

Diseño completo del proyecto: `docs/ROADMAP.md` (vive en disco en este repo y
es la fuente de verdad para todos los servicios, pero está en `.gitignore` a
propósito: es interno y no se publica por ahora — nunca agregarlo a un commit).

## Stack

- Spring Boot 4.1.0, Java 21, Gradle (no Maven, pese a lo que diga texto
  viejo — la dependencia a `lib-domain-foods` se resuelve vía GitHub
  Packages como repositorio Maven, pero el build tool es Gradle).
- Postgres 16 (contenedor `foodstore-postgres`, puerto **5432**).
- Liquibase para esquema.
- RabbitMQ (vía `supermercado-infra`): **consume** `pago.aprobado` (cola `productos.pago-aprobado` + DLQ) para
  descontar el stock y **publica** `stock.descuento-rechazado` (exchange `productos.events`, con *outbox*) cuando
  no alcanza. Contrato en `docs/events/`.
- Spring AI (`spring-ai-starter-model-anthropic`) para generar descripciones
  de producto — **sin API key configurada a propósito**, no gastar en eso
  salvo que el usuario lo pida explícitamente.
- Dominio (`Producto`, `Sku`, etc.) importado desde `lib-domain-foods`
  (versión `0.0.1-SNAPSHOT`, publicada a GitHub Packages).

## Comandos

```bash
docker compose up -d      # Postgres de este servicio
./gradlew bootRun          # arranca en localhost:8080 (requiere supermercado-infra: RabbitMQ)
./gradlew test             # unitarios
./gradlew intTest          # integración (Testcontainers, requiere Docker)
./gradlew jacocoFullReport   # cobertura test + intTest -> build/reports/jacoco/jacocoFullReport/index.html
./gradlew pitest              # tests de mutacion (solo unitarios) -> build/reports/pitest/index.html
./gradlew sonar              # analisis SonarQube (ver ../CLAUDE.md: requiere SONAR_TOKEN y el perfil `calidad`)
./gradlew build --refresh-dependencies   # tras un nuevo push a lib-domain-foods
```

## Puntos a tener presentes

- Si `lib-domain-foods` publicó una versión nueva (mismo string
  `0.0.1-SNAPSHOT`, contenido distinto), hay que correr con
  `--refresh-dependencies` para tomar los cambios — Gradle cachea snapshots.
- El Dockerfile/`docker-compose.yml` empaquetan la app completa en Docker;
  es para verificar el stack empaquetado de vez en cuando o desplegar, no
  para el día a día (eso es `./gradlew bootRun`).
- Repo en GitHub (`llocado/app-productos`), rama `main`, historial ya
  pusheado — seguir Conventional Commits en español de acá en adelante (ver
  reglas generales), aunque el historial previo no las siga.

- **CORS para `tienda-web`** (`config/CorsConfig`, propiedad `app.cors.allowed-origins`, env `CORS_ORIGINS`, por defecto
  `http://localhost:5173`): solo ese origen y solo lectura (`GET`, `HEAD`, `OPTIONS`) en `/api/**`; nunca `*`; las escrituras no se
  abren a ningún navegador. Es para desarrollo: en producción va el dominio real o un gateway. Si cambia el puerto del front,
  cambiar esta propiedad. Probado con `CorsConfigTest` y el request de Bruno `17`.

## Convenciones propias de este servicio

- CRUD completo de productos + paginación (`Pagina`, `CriterioPaginacion`,
  `listarPaginado` vienen de `lib-domain-foods`).
- Endpoint de generación de descripción vía IA, aislado del resto del CRUD.
- **Stock (único dueño: este servicio).** Un producto nace con stock 0; `POST /api/productos/{id}/stock/ingresos`
  (`{"unidades": 1..100000}`) suma unidades y es la única forma de cargarlo. Al aprobarse un pago
  (`pago.aprobado`) se descuenta **todo o nada** (`DescontarStockPorPagoUseCase`): primero se valida cada
  producto y solo si todos alcanzan se descuenta; si falta alguno (o está inactivo/no existe) no se toca nada y se
  publica `stock.descuento-rechazado`, que `pedidos-service` convierte en pedido `SIN_STOCK`. Idempotente por
  `eventId` (`eventos_procesados`). No hay reserva: puede haber más carritos que unidades hasta que se paga.
- **Bloqueo optimista en `ProductoEntity` (`@Version`, columna `productos.version`).** Dos pagos que compiten por la
  última unidad: uno confirma y el otro falla con `OptimisticLockingFailureException`, se reintenta (1 + 3) y ve el
  stock nuevo. Por eso `ProductoPersistenceAdapter.guardar` **carga la entidad gestionada y vuelca encima** (no
  construye una entidad nueva: con `@Version` una entidad sin versión se tomaría como nueva). Todo caso de uso que
  escriba el stock debe ser `@Transactional`.
- **Dominio y descuento:** el dominio (`Producto.reducirStock/aumentarStock`, `Stock`) vive en `lib-domain-foods`; la
  lógica de descuento por pago es de este servicio, no de la librería.
- `scripts/datos-prueba-productos.json` trae `stockInicial` por producto: no hay cargador automático, al importarlo hay
  que crear el producto y luego llamar al endpoint de ingreso.
- **Búsqueda por nombre** (`GET /api/productos?nombre=texto`): contiene el texto, sin distinguir mayúsculas, ordenada por nombre y
  paginada como el listado; `%` y `_` se buscan como texto literal (las consultas derivadas `...ContainingIgnoreCase` los
  escapan); máximo 100 caracteres. Distingue tildes ("platano" no encuentra "Plátano"): ver `../docs/seguridad-pendiente.md`.
- **Compilar con la librería local (sin GitHub Packages):** `./gradlew --include-build ../lib-domain-foods <tarea>` sustituye la
  dependencia por la carpeta local (mismo `group:name`). Sirve cuando el token venció o la librería tiene cambios sin publicar.
  No descomentar el `includeBuild` de `settings.gradle`: rompería el `Dockerfile`, que no tiene esa carpeta.
- **Credenciales de GitHub Packages:** `lib-domain-foods` se resuelve con `gprUser`/`gprKey` (`gradle.properties`,
  fuera de git). Si GitHub responde `401`, el token venció: regenerarlo. Mientras tanto `./gradlew --offline ...`
  usa la copia en caché.
- Los tests que no levantan RabbitMQ fijan `spring.rabbitmq.listener.simple.auto-startup=false` y
  `spring.rabbitmq.port=1`, para no conectarse nunca al broker de desarrollo.
