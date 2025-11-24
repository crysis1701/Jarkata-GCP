package org.jarkata.gcp.helidongcp.config;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Configuration class for Google Cloud Spanner connection settings.
 * Uses CDI to inject configuration properties from application.yaml.
 */
@ApplicationScoped
@SuppressWarnings("unused")
public class SpannerConfig {

    private final String projectId;
    private final String instanceId;
    private final String databaseId;
    private final String credentialsPath;
    private final boolean failOnMissingCredentials;

    @Inject
    public SpannerConfig(
            @ConfigProperty(name = "spanner.project-id") String projectId,
            @ConfigProperty(name = "spanner.instance-id") String instanceId,
            @ConfigProperty(name = "spanner.database-id") String databaseId,
            @ConfigProperty(name = "spanner.credentials-path", defaultValue = "") String credentialsPath,
            @ConfigProperty(name = "spanner.fail-on-missing-credentials", defaultValue = "false") boolean failOnMissingCredentials) {
        this.projectId = projectId;
        this.instanceId = instanceId;
        this.databaseId = databaseId;
        this.credentialsPath = credentialsPath;
        this.failOnMissingCredentials = failOnMissingCredentials;
    }

    public String projectId() {
        return projectId;
    }

    public String instanceId() {
        return instanceId;
    }

    public String databaseId() {
        return databaseId;
    }

    public String credentialsPath() {
        return credentialsPath;
    }

    public boolean failOnMissingCredentials() {
        return failOnMissingCredentials;
    }

    public String databaseName() {
        return "projects/%s/instances/%s/databases/%s".formatted(projectId, instanceId, databaseId);
    }
}
