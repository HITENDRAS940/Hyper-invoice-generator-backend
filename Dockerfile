# ═══════════════════════════════════════════════════════════════════════════
# Multi-stage Dockerfile — HyperInvoiceBackend
# Stateless service: no database, no migrations.
# Memory-optimised for Render Free Tier (512 MB RAM).
# ═══════════════════════════════════════════════════════════════════════════

# ───────────────────────────────────────────────────────────────────────────
# Stage 1 : Dependency cache
#   Copy ONLY pom.xml + mvnw — this layer is only invalidated when
#   dependencies change, not on every source-code edit.
# ───────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS deps

WORKDIR /build

COPY mvnw .
COPY .mvn .mvn
RUN chmod +x mvnw

COPY pom.xml .

# Resolve all deps into the local Maven repo — cached until pom.xml changes
RUN ./mvnw dependency:go-offline -B --no-transfer-progress

# ───────────────────────────────────────────────────────────────────────────
# Stage 2 : Build
#   Inherits the warm Maven cache from Stage 1.
#   Only invalidated when src/ changes.
# ───────────────────────────────────────────────────────────────────────────
FROM deps AS build

WORKDIR /build

COPY src ./src

# Strip debug info to keep the JAR lean
RUN ./mvnw clean package -DskipTests -B --no-transfer-progress \
    -Dmaven.compiler.debug=false \
    -Dmaven.compiler.debuglevel=none

# ───────────────────────────────────────────────────────────────────────────
# Stage 3 : Runtime
#   JRE-only Alpine image — no JDK, no Maven, no source code.
#   Fonts are required by OpenHTMLToPDF / PDFBox for PDF rendering.
# ───────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Fonts required by OpenHTMLToPDF at runtime
RUN apk add --no-cache \
        fontconfig \
        ttf-dejavu \
    && rm -rf /var/cache/apk/*

# Run as a non-root user
RUN addgroup -g 1001 -S appgroup && \
    adduser  -u 1001 -S appuser -G appgroup

WORKDIR /app

COPY --from=build /build/target/HyperInvoiceBackend-0.0.1-SNAPSHOT.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

# ───────────────────────────────────────────────────────────────────────────
# JVM Memory Budget — 512 MB Render Free Tier (stateless, no DB)
#
#   Heap          240 MB  (-Xms140m / -Xmx240m)
#   Metaspace     128 MB  (OpenHTMLToPDF + Spring load ~100-110 MB of classes)
#   Code cache     20 MB
#   Direct mem     20 MB  (Cloudinary + HTTP client buffers)
#   Thread stacks  ~40 MB
#   Native / OS    ~64 MB
#   ─────────────────────
#   Total         ~512 MB
#
#   SerialGC            → lowest per-GC memory overhead, ideal for single-core
#   TieredStopAtLevel=1 → skip C2 JIT; saves ~50 MB RSS + faster startup
#   ExitOnOutOfMemory   → let Render restart the container cleanly on OOM
# ───────────────────────────────────────────────────────────────────────────
ENTRYPOINT ["java", \
  "-Xms140m", \
  "-Xmx240m", \
  "-XX:MaxMetaspaceSize=128m", \
  "-XX:MetaspaceSize=96m", \
  "-XX:ReservedCodeCacheSize=20m", \
  "-XX:MaxDirectMemorySize=20m", \
  "-XX:+UseSerialGC", \
  "-XX:+UseContainerSupport", \
  "-XX:ActiveProcessorCount=2", \
  "-XX:+TieredCompilation", \
  "-XX:TieredStopAtLevel=1", \
  "-XX:+UseCompressedOops", \
  "-XX:+UseCompressedClassPointers", \
  "-XX:-UsePerfData", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-XX:+HeapDumpOnOutOfMemoryError", \
  "-XX:HeapDumpPath=/tmp/heapdump.hprof", \
  "-XX:StringTableSize=10000", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dfile.encoding=UTF-8", \
  "-Djava.awt.headless=true", \
  "-jar", "app.jar"]

# ───────────────────────────────────────────────────────────────────────────
# Health Check
#   start-period=60s — no DB/Flyway startup, Spring boots faster now.
# ───────────────────────────────────────────────────────────────────────────
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
