# ── Etapa 1: Build ──────────────────────────────────────────────
FROM gradle:8.5-jdk21 AS build

WORKDIR /app

# Copiar archivos de configuración primero (mejor cache de capas)
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

# Descargar dependencias (aprovecha caché de Docker si build.gradle no cambia)
RUN gradle dependencies --no-daemon || true

# Copiar el código fuente
COPY src ./src

# Construir el JAR ejecutable, saltando tests
RUN gradle bootJar -x test -x check --no-daemon

# ── Etapa 2: Runtime ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copiar solo el JAR desde la etapa de build
COPY --from=build /app/build/libs/prueba-1.0.jar app.jar

# Exponer el puerto 10000 requerido por el proxy de Render
EXPOSE 10000

# Arrancar la aplicación con optimización de memoria para Render (512MB RAM) vinculando a 0.0.0.0
ENTRYPOINT ["sh", "-c", "exec java -XX:+UseSerialGC -Xss512k -XX:MaxRAMPercentage=75.0 -Dserver.port=${PORT:-10000} -Dserver.address=0.0.0.0 -jar app.jar"]
