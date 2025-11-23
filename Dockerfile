# dockerfile
# Build JAR with standard JDK (Temurin 21) then create native image (GraalVM 21)
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY src ./src
# Helidon native profile (adjust if your profile name differs)
RUN --mount=type=cache,target=/root/.m2 mvn -B -Pnative-image -DskipTests package
# Native executable should now be in target/ (usually named after artifactId)

FROM ghcr.io/graalvm/native-image:25 AS refine
WORKDIR /workspace
# (Optional) If the Maven plugin already produced the binary, you can skip extra native-image invocation.
# Example manual build (uncomment if needed):
# COPY --from=build /workspace/target/app.jar /workspace/app.jar
# RUN native-image --no-fallback -H:Name=app -jar app.jar

FROM gcr.io/distroless/cc-debian12 AS runtime
WORKDIR /app
# Copy only the native binary. Replace my-app with your artifactId binary name.
# List target/ locally after a build to confirm the exact filename.
COPY --from=build /workspace/target/my-app /app/app
ENV PORT=8080
EXPOSE 8080
USER 65532:65532
ENTRYPOINT ["/app/app"]
