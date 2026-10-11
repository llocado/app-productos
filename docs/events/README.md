# Eventos de app-productos

Contrato del bus de eventos (RabbitMQ) segun el ROADMAP del proyecto (seccion "Contrato de eventos"). Cada evento va en el envelope comun (`eventId`, `eventType`, `eventVersion`, `occurredAt`, `producer`, `correlationId`, `payload`). El `correlationId` es el id del checkout y se propaga desde `pago.aprobado`.

## Publica (productor)

Exchange `productos.events` (topic), routing key = `eventType`. Se publica con el patron *outbox*: el evento se guarda en la tabla `eventos_salida` en la misma transaccion que la marca de evento procesado, y `PublicadorOutbox` lo envia despues con confirmacion de RabbitMQ (solo se marca publicado si el broker confirma **y** alguna cola lo recibio).

- **`stock.descuento-rechazado`** — [stock.descuento-rechazado.schema.json](./stock.descuento-rechazado.schema.json). Se publica cuando un `pago.aprobado` no pudo descontar el stock. Lleva `pagoId`, `pedidoId`, `usuarioId` y la lista de `faltantes` (`productoId`, `solicitado`, `disponible`). Consumido por `pedidos-service`, que pasa el pedido de `PAGADO` a `SIN_STOCK`. El `eventId` es determinista (`stock.descuento-rechazado` + `pagoId`).
  - **Si el descuento se logra no se publica nada** (no hay evento `stock.descontado`): el exito es silencioso.

## Consume (consumidor)

- **`pago.aprobado`** — contrato en `pagos-service/docs/events/pago.aprobado.schema.json` (la fuente de verdad es el productor). Exchange `pagos.events`, cola `productos.pago-aprobado` (con `productos.pago-aprobado.dlq`). Se descuenta el stock de cada item pagado **todo o nada**: primero se valida cada producto y solo si todos alcanzan se descuenta; si alguno no alcanza (no existe, esta inactivo o tiene menos unidades) no se toca nada y se publica `stock.descuento-rechazado`. Items repetidos del mismo producto se suman. Un payload invalido (pagoId, pedidoId, usuario de hasta 100 caracteres, 1 a 500 items, cantidades de 1 a 1.000.000) va a la DLQ sin reintentos.

## Errores y reintentos

Un evento que no se puede procesar nunca se reencola sin limite. Si es irrecuperable (JSON ilegible, payload invalido) va a la DLQ de inmediato. Si el fallo es transitorio (la base no responde, o un conflicto de concurrencia porque otro pago descontaba el mismo producto) se reintenta 1 intento + 3 reintentos (esperas de 1, 2 y 4 s) y luego va a la DLQ. Al reintentar se lee el stock actualizado.

## Idempotencia y concurrencia

Un `eventId` ya visto se descarta (tabla `eventos_procesados`): descontar dos veces vendería unidades que no existen. Cada producto lleva `@Version` (bloqueo optimista): si dos pagos compiten por la ultima unidad, uno confirma y el otro falla, se reintenta y ve el stock nuevo.

## Limitaciones conocidas

- No hay reserva de stock: puede haber mas carritos que unidades hasta que se paga. Si al pagar ya no alcanza, el pedido queda `SIN_STOCK` (gestion manual, sin reembolso automatico: el dinero es simulado).
- `eventos_procesados` y `eventos_salida` crecen sin limite (retencion pendiente, ver `docs/seguridad-pendiente.md`).
