# app-productos (productos-service)

Catálogo del proyecto supermercado. Primer servicio construido, el más
maduro de todos. Reglas compartidas con el resto del proyecto en
`../CLAUDE.md` (arquitectura hexagonal, commits, verificación, etc.) —
este archivo solo cubre lo específico de este repo.

Diseño completo del proyecto: `docs/ROADMAP.md` (vive en este repo, es la
fuente de verdad para todos los demás servicios también).

## Stack

- Spring Boot 4.1.0, Java 21, Gradle (no Maven, pese a lo que diga texto
  viejo — la dependencia a `lib-domain-foods` se resuelve vía GitHub
  Packages como repositorio Maven, pero el build tool es Gradle).
- Postgres 16 (contenedor `foodstore-postgres`, puerto **5432**).
- Liquibase para esquema.
- Spring AI (`spring-ai-starter-model-anthropic`) para generar descripciones
  de producto — **sin API key configurada a propósito**, no gastar en eso
  salvo que el usuario lo pida explícitamente.
- Dominio (`Producto`, `Sku`, etc.) importado desde `lib-domain-foods`
  (versión `0.0.1-SNAPSHOT`, publicada a GitHub Packages).

## Comandos

```bash
docker compose up -d      # Postgres de este servicio
./gradlew bootRun          # arranca en localhost:8080
./gradlew test             # unitarios
./gradlew intTest          # integración (Testcontainers, requiere Docker)
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

## Convenciones propias de este servicio

- CRUD completo de productos + paginación (`Pagina`, `CriterioPaginacion`,
  `listarPaginado` vienen de `lib-domain-foods`).
- Endpoint de generación de descripción vía IA, aislado del resto del CRUD.
