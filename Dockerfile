# syntax=docker/dockerfile:1

# ---- Etapa de build: compila el jar ejecutable con Gradle ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copiamos primero solo lo necesario para resolver dependencias: si despues
# cambia el codigo fuente pero no build.gradle, Docker reusa esta capa.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

# lib-domain-foods se resuelve desde GitHub Packages (repo privado). Las
# credenciales se pasan como build secret de BuildKit: se leen en tiempo de
# build y NUNCA quedan escritas en ninguna capa de la imagen (a diferencia de
# un ARG o de copiar gradle.properties, que si persisten en el historial).
RUN --mount=type=secret,id=github_actor \
    --mount=type=secret,id=github_token \
    export GITHUB_ACTOR=$(cat /run/secrets/github_actor) && \
    export GITHUB_TOKEN=$(cat /run/secrets/github_token) && \
    ./gradlew dependencies --no-daemon

COPY src ./src

RUN --mount=type=secret,id=github_actor \
    --mount=type=secret,id=github_token \
    export GITHUB_ACTOR=$(cat /run/secrets/github_actor) && \
    export GITHUB_TOKEN=$(cat /run/secrets/github_token) && \
    ./gradlew bootJar --no-daemon -x test && \
    cp $(ls build/libs/*.jar | grep -v plain) /app/app.jar

# ---- Etapa final: solo el JRE + el jar ya compilado (imagen liviana) ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /app/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
