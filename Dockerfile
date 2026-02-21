# ═══════════════════════════════════════════════════════════════════════════
# Multi-stage Dockerfile — HyperInvoiceBackend
# Memory-optimised for Render Free Tier (512 MB RAM)
# ═══════════════════════════════════════════════════════════════════════════

# ───────────────────────────────────────────────────────────────────────────
# Stage 1 : Dependency cache
#   Copy ONLY pom.xml + mvnw first so this layer is only invalidated
#   when dependencies actually change, not on every source-code edit.
# ───────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS deps

WORKDIR /build

# Maven wrapper
COPY mvnw .
COPY .mvn .mvn
RUN chmod +x mvnw

# Dependency descriptor only — source is NOT copied yet
COPY pom.xml .

# Pull every dependency & plugin into the local repo.
# This layer is Docker-cached until pom.xml changes.
RUN ./mvnw dependency:go-offline -B --no-transfer-progress

# ───────────────────────────────────────────────────────────────────────────
# Stage 2 : Build
#   Inherits the warm Maven cache from Stage 1.
#   Only invalidated when src/ changes.
# ───────────────────────────────────────────────────────────────────────────
FROM deps AS build

WORKDIR /build

# Source code (cache-busts only this layer and below)
COPY src ./src

# Package — strip debug info to shrink the JAR slightly
RUN ./mvnw clean package -DskipTests -B --no-transfer-progress \
    -Dmaven.compiler.debug=false \
    -Dmaven.compiler.debuglevel=none

# ───────────────────────────────────────────────────────────────────────────
# Stage 3 : Runtime
#   Minimal JRE-only Alpine image.
#   Includes fonts required by OpenHTMLToPDF for PDF generation.
# ───────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Fonts needed by OpenHTMLToPDF / PDFBox at runtime
RUN apk add --no-cache \
        fontconfig \
        ttf-dejavu \
    && rm -rf /var/cache/apk/*

# Non-root user for security
RUN addgroup -g 1001 -S appgroup && \
    adduser  -u 1001 -S appuser -G appgroup

WORKDIR /app

# Copy the fat JAR produced in Stage 2
COPY --from=build /build/target/HyperInvoiceBackend-0.0.1-SNAPSHOT.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

# ───────────────────────────────────────────────────────────────────────────
# JVM Memory Budget for 512 MB container (Render Free Tier)
#
#   Heap          240 MB  (-Xms140m / -Xmx240m)
#   Metaspace     128 MB  (OpenHTMLToPDF + Spring need ~100-110 MB)
#   Code cache     20 MB
#   Direct mem     20 MB
#   Thread stacks  ~40 MB
#   Native/OS      ~64 MB
#   ─────────────────────
#   Total         ~512 MB
#
#   SerialGC  → lowest GC overhead, best for single-core free tier
#   TieredStopAtLevel=1 → fast startup, skip C2 JIT (saves ~50 MB RSS)
# ───────────────────────────────────────────────────────────────────────────
ENTRYPOINT ["sh", "-c", "\
  if [ -n \"$DATABASE_URL\" ] && echo \"$DATABASE_URL\" | grep -q '^postgres://'; then \
    export DATABASE_URL=$(echo $DATABASE_URL | sed 's|^postgres://|jdbc:postgresql://|'); \
  fi && \
  exec java \
    -Xms140m \
    -Xmx240m \
    -XX:MaxMetaspaceSize=128m \
    -XX:MetaspaceSize=96m \
    -XX:ReservedCodeCacheSize=20m \
    -XX:MaxDirectMemorySize=20m \
    -XX:+UseSerialGC \
    -XX:+UseContainerSupport \
    -XX:ActiveProcessorCount=2 \
    -XX:+TieredCompilation \
    -XX:TieredStopAtLevel=1 \
    -XX:+UseCompressedOops \
    -XX:+UseCompressedClassPointers \
    -XX:-UsePerfData \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/tmp/heapdump.hprof \
    -XX:StringTableSize=10000 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dfile.encoding=UTF-8 \
    -Djava.awt.headless=true \
    -jar app.jar"]

# ───────────────────────────────────────────────────────────────────────────
# Health Check
#   start-period=90s gives Spring + Flyway time to fully boot.
# ───────────────────────────────────────────────────────────────────────────
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
