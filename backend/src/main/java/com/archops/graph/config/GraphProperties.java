package com.archops.graph.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archops.graph")
public class GraphProperties {

    private String uri = "bolt://localhost:7687";
    private String username = "neo4j";
    private String password = "archopsneo4j";
    private String database = "neo4j";

    /** Staging vault TTL for proposal-bound secrets. */
    private Duration credentialStagingTtl = Duration.ofHours(1);

    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }
    public Duration getCredentialStagingTtl() { return credentialStagingTtl; }
    public void setCredentialStagingTtl(Duration credentialStagingTtl) {
        this.credentialStagingTtl = credentialStagingTtl;
    }
}
