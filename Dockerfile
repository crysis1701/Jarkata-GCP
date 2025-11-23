# Optimized JVM Dockerfile for Helidon MicroProfile application (Cloud Run)
# NOTE: Helidon 4 is validated on Java 21; Java 25 is bleeding edge. Revert to 21 if issues arise.

# ---- Builder stage ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

# Leverage dependency caching
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

# Copy sources
COPY src ./src

# Build JAR (no native profile; it does not exist yet)
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre-alpine AS runtime

RUN addgroup -S app && adduser -S -G app -u 10001 app
WORKDIR /app

# Copy artifacts
COPY --from=build /workspace/target/Helidon-GCP.jar ./
COPY --from=build /workspace/target/libs ./libs

ENV JAVA_OPTS="" \
    PORT=8080 \
    APP_JAR=Helidon-GCP.jar

EXPOSE 8080

# Healthcheck (basic) - adjust endpoint if you enable health feature explicitly
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 CMD wget -qO- http://localhost:8080/ > /dev/null || exit 1

USER app
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/$APP_JAR"]

LABEL org.opencontainers.image.title="Helidon-GCP" \
      org.opencontainers.image.description="Helidon MicroProfile application (JVM mode)" \
      org.opencontainers.image.version="1.0-SNAPSHOT" \
      org.opencontainers.image.licenses="Apache-2.0"
