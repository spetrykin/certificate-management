# syntax=docker/dockerfile:1

# --- Build stage -------------------------------------------------------
# Builds the jar from source so `docker compose up --build` (or a plain
# `docker build`) is self-contained — no pre-built jar required on the host.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Dependencies first, so this layer is cached across source-only changes.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src src
RUN ./mvnw -B package -DskipTests

# --- Runtime stage -------------------------------------------------------
# JRE only — the build toolchain never ships in the runtime image.
FROM eclipse-temurin:25-jre AS run
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
