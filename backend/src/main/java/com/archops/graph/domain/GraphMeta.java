package com.archops.graph.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "graph_meta")
public class GraphMeta {

    public static final String GLOBAL_KEY = "global";

    @Id
    @Column(length = 64)
    private String key = GLOBAL_KEY;

    @Column(name = "graph_version", nullable = false)
    private long graphVersion;

    @Column(name = "neo4j_bookmark", columnDefinition = "text")
    private String neo4jBookmark;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public long getGraphVersion() { return graphVersion; }
    public void setGraphVersion(long graphVersion) { this.graphVersion = graphVersion; }
    public String getNeo4jBookmark() { return neo4jBookmark; }
    public void setNeo4jBookmark(String neo4jBookmark) { this.neo4jBookmark = neo4jBookmark; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
