# dockerfile
# Build and create native image with GraalVM 25
FROM ghcr.io/graalvm/jdk:25 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY src ./src
# Build using Helidon native profile (adjust if your profile differs)
RUN --mount=type=cache,target=/root/.m2 mvn -B -Pnative-image -DskipTests package

# Binary should be under target directory (name depends on your artifactId)
# Replace app with your final native executable name if different.
FROM gcr.io/distroless/cc-debian12 AS runtime
WORKDIR /app
COPY --from=build /workspace/target/* /app/
ENV PORT=8080
EXPOSE 8080
USER 65532:65532
ENTRYPOINT ["/app/app"]
