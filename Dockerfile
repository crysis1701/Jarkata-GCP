# dockerfile
# Build JAR with Maven (Temurin JDK 25)
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

# Compile native image with GraalVM 25
FROM ghcr.io/graalvm/native-image:25 AS native-build
WORKDIR /workspace
# Copy app artifacts (thin JAR + libs). If your build produces an uber/fat JAR, you can copy only the JAR.
COPY --from=build /workspace/target/Helidon-GCP.jar /workspace/app.jar
COPY --from=build /workspace/target/libs /workspace/libs
# Build native binary; relies on JAR manifest for Main-Class and Class-Path
# Adjust memory/flags if needed for your project.
RUN native-image --no-fallback -J-Xmx4g -H:Name=app -jar /workspace/app.jar

# Minimal runtime (distroless, glibc)
FROM gcr.io/distroless/cc-debian12 AS runtime
WORKDIR /app
COPY --from=native-build /workspace/app /app/app

# Cloud Run expects port 8080
ENV PORT=8080
EXPOSE 8080

# Non-root user in distroless
USER 65532:65532

ENTRYPOINT ["/app/app"]
