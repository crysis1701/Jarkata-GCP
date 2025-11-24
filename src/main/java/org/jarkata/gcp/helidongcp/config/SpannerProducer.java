package org.jarkata.gcp.helidongcp.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CDI producer for Google Cloud Spanner {@link DatabaseClient} and
 * {@link Spanner}.
 * Handles initialization, credentials loading, and lifecycle management.
 */
@ApplicationScoped
public class SpannerProducer {

    private static final Logger LOGGER = Logger.getLogger(SpannerProducer.class.getName());

    private Spanner spanner;

    private DatabaseClient databaseClient;

    @Inject
    private SpannerConfig config;

    @PostConstruct
    void init() {
        var start = Instant.now();
        LOGGER.info("Step 1: Initialize connection to Google Cloud Spanner started");
        LOGGER.info(() -> "Step 2: Configuration loaded: projectId=" + config.projectId() + ", instanceId="
                + config.instanceId() + ", databaseId=" + config.databaseId());

        GoogleCredentials credentials;
        try {
            credentials = createCredentials(config.credentialsPath());
            LOGGER.info(credentials == null ? "Step 3: Using Application Default Credentials"
                    : "Step 3: Using explicit credentials file");
        } catch (IllegalStateException e) {
            // Missing credentials file and the behavior depends on fail flag
            if (config.failOnMissingCredentials()) {
                LOGGER.log(Level.SEVERE, "Step 3 FAILED: " + e.getMessage(), e);
                throw e; // rethrow to fail fast
            } else {
                LOGGER.log(Level.WARNING,
                        "Step 3: Credentials not found, falling back to Application Default Credentials: "
                                + e.getMessage());
                credentials = null;
            }
        }

        var builder = SpannerOptions.newBuilder().setProjectId(config.projectId());
        if (credentials != null) {
            builder.setCredentials(credentials);
            LOGGER.fine("Credentials set on SpannerOptions builder");
        }
        LOGGER.info("Step 4: Building Spanner service");
        spanner = builder.build().getService();
        LOGGER.info("Step 5: Creating DatabaseClient");
        databaseClient = spanner.getDatabaseClient(
                com.google.cloud.spanner.DatabaseId.of(config.projectId(), config.instanceId(),
                        config.databaseId()));
        var elapsed = Duration.between(start, Instant.now()).toMillis();
        LOGGER.info(() -> "Step 6: Spanner connection ready (" + elapsed + " ms)");
    }

    private GoogleCredentials createCredentials(String credentialsPath) {
        if (credentialsPath.isBlank()) {
            LOGGER.fine("No credentials-path provided; will rely on ADC if available");
            return null; // Application Default Credentials if running in GCP environment
        }

        // Try absolute or project-relative file path first
        var filePath = Path.of(credentialsPath);
        if (!filePath.isAbsolute()) {
            // try project-relative (use user.dir)
            var projectRelative = Path.of(System.getProperty("user.dir")).resolve(credentialsPath);
            if (Files.exists(projectRelative)) {
                filePath = projectRelative;
            }
        }

        if (Files.exists(filePath)) {
            LOGGER.info("Found credentials file on filesystem: " + filePath);
            try (var in = new FileInputStream(filePath.toFile())) {
                return GoogleCredentials.fromStream(in);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load credentials from file: " + filePath, e);
            }
        }

        // Next try classpath resource lookup
        var classpathKey = normalizeClasspathKey(credentialsPath);
        InputStream resourceStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathKey);
        if (resourceStream == null) {
            // try with leading slash
            resourceStream = SpannerProducer.class.getResourceAsStream('/' + classpathKey);
        }
        if (resourceStream != null) {
            LOGGER.info("Found credentials on classpath: " + classpathKey);
            try (var in = resourceStream) {
                return GoogleCredentials.fromStream(in);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load credentials from classpath resource: " + classpathKey,
                        e);
            }
        }

        // Not found: throw with detailed message so caller can decide to fail or
        // fallback
        var attempted = "filesystem: " + filePath + ", classpath: " + classpathKey + ", projectDir: "
                + System.getProperty("user.dir");
        throw new IllegalStateException("Spanner credentials file not found. Tried: " + attempted);
    }

    private String normalizeClasspathKey(String credentialsPath) {
        // Remove leading src/main/resources if provided and any leading slashes
        var key = credentialsPath.replaceFirst("^src/main/resources/", "");
        key = key.replaceAll("^/+", "");
        return key;
    }

    @Produces
    @ApplicationScoped
    public DatabaseClient databaseClient() {
        return databaseClient;
    }

    @Produces
    @ApplicationScoped
    public Spanner spanner() {
        return spanner;
    }

    @PreDestroy
    void close() {
        LOGGER.info("Shutting down Spanner resources");
        if (Objects.nonNull(spanner)) {
            spanner.close();
            LOGGER.info("Spanner service closed");
        }
    }

    void eagerInit(@Observes @Initialized(ApplicationScoped.class) Object init) {
        // Trigger init method implicitly by accessing databaseClient; init already runs
        // due to @PostConstruct.
        LOGGER.info("Eager initialization event observed for SpannerProducer");
    }
}
