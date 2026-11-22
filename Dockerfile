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

# ── Class Data Sharing (CDS) archive ────────────────────────
# Generate a shared class archive at image-build time.  On every cold start
# the JVM memory-maps pre-parsed class data instead of re-parsing the entire
# 81 MB fat JAR — cutting startup class-loading by ~25-35 seconds.
#
# The app is started with a dummy datasource so Spring loads its full class
# graph before hitting a connection error and exiting.  The non-zero exit is
# expected (|| true) and the archive is written before the exit occurs.
RUN java -Djava.awt.headless=true \
         -XX:ArchiveClassesAtExit=/app/app-cds.jsa \
         -jar app.jar \
         --spring.profiles.active=prod \
         --spring.datasource.url=jdbc:postgresql://localhost/dummy \
         --spring.datasource.hikari.initialization-fail-timeout=1 \
         --spring.flyway.enabled=false \
     2>/dev/null || true

RUN chown -R appuser:appgroup /app

USER appuser

# Render injects $PORT at runtime; Spring Boot reads it via
# server.port=${PORT:${SERVER_PORT:8081}} in application.properties
EXPOSE 8081

# JVM tuning for container environments:
#   -XX:+UseContainerSupport        – respects cgroup memory limits
#   -XX:MaxRAMPercentage=75.0       – leaves headroom for the OS and PDFBox
#   -XX:+UseSerialGC                – lower GC overhead on small heaps (< 1 GB)
#   -XX:MaxMetaspaceSize=256m       – PDFBox + iText7 + Spring need ~180-220 MB
#   -Djava.awt.headless=true        – required for PDFBox ImageIO
#   -XX:SharedArchiveFile=app-cds.jsa – use the pre-built CDS archive to skip
#                                      re-parsing classes on every cold start
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseSerialGC", \
  "-XX:MaxMetaspaceSize=256m", \
  "-Djava.awt.headless=true", \
  "-XX:SharedArchiveFile=/app/app-cds.jsa", \
  "-jar", "app.jar"]
