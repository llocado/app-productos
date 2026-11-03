# app-productos (productos-service)

[![Tests](https://github.com/llocado/app-productos/actions/workflows/tests.yml/badge.svg)](https://github.com/llocado/app-productos/actions/workflows/tests.yml)

Catálogo del proyecto supermercado y **único dueño del stock**. Arquitectura hexagonal: casos de uso en `application/usecase`,
puertos en `application/port`, adaptadores en `infrastructure/*`. El dominio (`Producto`, `Sku`, `Precio`...) vive en una biblioteca
aparte, [`lib-domain-foods`](https://github.com/llocado/lib-domain-foods).

Este servicio es una pieza de un sistema mayor (catálogo, carrito, pedidos, pagos, Keycloak y tienda web). El diseño completo, el diagrama
de eventos y la demo de un comando están en el repo [`supermercado`](https://github.com/llocado/supermercado).

## Qué hace

- **Catálogo REST** (`/api/productos`): listar con paginación y búsqueda por nombre (sin distinguir mayúsculas ni tildes, con la extensión
  `unaccent` de Postgres), ver por id o por SKU, crear, actualizar y borrar.
- **Stock**: se carga con `POST /api/productos/{id}/stock/ingresos` y se descuenta al consumir el evento `pago.aprobado`.
- **Descripciones con IA** (`POST /api/productos/generar-descripcion`, Spring AI): desactivado a propósito, no hay API key configurada.

## Decisiones de diseño

- **El stock se descuenta cuando se paga, no al agregar al carrito.** No hay reserva: puede haber más carritos que unidades. Si al pagar ya no
  alcanza, se publica `stock.descuento-rechazado` y el pedido queda `SIN_STOCK`.
- **Bloqueo optimista (`@Version`)** en cada producto: dos descuentos simultáneos no se pisan; el que pierde reintenta.
- **Idempotencia por `eventId`** (tabla `eventos_procesados`): un evento repetido se descarta, porque descontar dos veces vendería unidades que
  no existen. El `eventId` se reserva *antes* de actuar.
- **Outbox transaccional** para publicar `stock.descuento-rechazado`: el evento se guarda en la misma transacción que el cambio y un publicador lo
  envía después con confirmación de RabbitMQ y `mandatory`. Nunca se publica antes del commit.
- **Mensajes que fallan nunca se reencolan sin límite**: payload inválido va directo a la DLQ; un fallo transitorio, 1 intento + 3 reintentos
  (1 s, 2 s, 4 s) y luego DLQ.
- **Cada servicio es dueño de su modelo**: nadie importa el dominio de otro servicio.
- **Cambios de esquema solo con Liquibase**, nunca `ddl-auto: update`.

Contrato de los eventos (JSON Schema): [`docs/events/`](docs/events/README.md).

## Cómo ejecutarlo

Requiere Java 21, Docker y un token de GitHub con permiso `read:packages` para descargar `lib-domain-foods` (GitHub Packages exige
autenticación incluso para paquetes públicos). Guárdalo en `~/.gradle/gradle.properties`, **nunca en el repo**:

```properties
gprUser=tu-usuario
gprKey=tu-token
```

```bash
docker compose up -d      # Postgres de este servicio (puerto 5432, solo 127.0.0.1)
./gradlew bootRun         # http://localhost:8080 (necesita RabbitMQ de supermercado-infra para los eventos)
./gradlew test            # unitarios
./gradlew intTest         # integración con Testcontainers (requiere Docker)
./gradlew pitest          # tests de mutación (solo unitarios)
```

Para probarlo sin configurar nada, usa la demo de un comando del repo [`supermercado`](https://github.com/llocado/supermercado).

## Pruebas

Unitarias, de integración con Postgres y RabbitMQ reales (Testcontainers), de mutación con PIT y una colección de [Bruno](https://www.usebruno.com/)
en `bruno/` con una request por endpoint (repetibles: generan sus propios SKU y limpian lo que crean).

## Límites conocidos

- Esta API **no exige autenticación todavía** (el catálogo es público; las escrituras deberían exigir rol `admin`). Es un riesgo conocido y
  documentado, pendiente de cerrar antes de cualquier despliegue real.
- `eventos_procesados` y `eventos_salida` crecen sin límite: falta una política de retención.
