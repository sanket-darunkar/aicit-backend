# ============================================================
# AICIT Backend – Multi-stage Docker build
# Stage 1 : Build the fat JAR with Maven
# Stage 2 : Run on a minimal JRE 21 image
# ============================================================

# ── Stage 1: Build ──────────────────────────────────────────
FROM maven:3-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

# Copy dependency manifests first so Docker can cache the
# dependency-download layer separately from the source code.
COPY pom.xml .

# Download dependencies (offline-friendly cache layer)
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B 2>/dev/null || true

# Copy full source and build, skipping tests
# (tests require a live DB; run them in CI before building the image)
COPY src/ src/
RUN mvn package -DskipTests -B

# ── Stage 2: Runtime ────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy only the executable JAR from the build stage
COPY --from=build /workspace/target/*.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

# Render injects $PORT at runtime; Spring Boot reads it via
# server.port=${PORT:${SERVER_PORT:8081}} in application.properties
EXPOSE 8081

# JVM tuning for container environments:
#   -XX:+UseContainerSupport   – respects cgroup memory limits
#   -XX:MaxRAMPercentage=75    – leaves headroom for the OS
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
