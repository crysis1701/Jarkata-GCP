# Multi-stage optimized Dockerfile for Helidon MicroProfile application
# Build stage caches dependencies separately for faster incremental builds.

# ---- Builder stage ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

# Copy pom first to leverage dependency caching
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline

# Copy source
COPY src ./src

# Build (adjust -DskipTests if you want tests executed)
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests package

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre-alpine AS runtime

# Create non-root user (uid 10001 chosen arbitrarily)
RUN addgroup -S app && adduser -S -G app -u 10001 app

WORKDIR /app

# Copy application artifacts (jar + any libs if present)
COPY --from=build /workspace/target/Helidon-GCP.jar ./
COPY --from=build /workspace/target/libs ./libs

# Environment configuration
ENV JAVA_OPTS="" \
    PORT=8080 \
    APP_JAR=Helidon-GCP.jar

# Expose port 8080 for Cloud Run compatibility
EXPOSE 8080

# Basic healthcheck using OpenAPI endpoint (present via dependencies) fallback to 200 on port
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 CMD wget -qO- http://localhost:8080/openapi > /dev/null 2>&1 || wget -qO- http://localhost:8080/ || exit 1

USER app

# Use sh -c to allow JAVA_OPTS expansion
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/$APP_JAR"]

# OCI labels (adjust revision via build args if desired)
LABEL org.opencontainers.image.title="Helidon-GCP" \
      org.opencontainers.image.description="Helidon MicroProfile application container" \
      org.opencontainers.image.source="https://example.com/repo" \
      org.opencontainers.image.version="1.0-SNAPSHOT" \
      org.opencontainers.image.licenses="Apache-2.0"
