// ArchOps Neo4j schema — applied by Neo4jSchemaInitializer on startup
// Statements are executed one-by-one (Neo4j does not allow multi-statement in one run).

CREATE CONSTRAINT asset_element_id IF NOT EXISTS FOR (n:Asset) REQUIRE n.elementId IS UNIQUE;
CREATE CONSTRAINT asset_pg_id IF NOT EXISTS FOR (n:Asset) REQUIRE n.pgAssetId IS UNIQUE;
CREATE CONSTRAINT tag_slug IF NOT EXISTS FOR (n:Tag) REQUIRE n.slug IS UNIQUE;

CREATE CONSTRAINT rel_member_of_element_id IF NOT EXISTS FOR ()-[r:MEMBER_OF]-() REQUIRE r.elementId IS UNIQUE;
CREATE CONSTRAINT rel_runs_on_element_id IF NOT EXISTS FOR ()-[r:RUNS_ON]-() REQUIRE r.elementId IS UNIQUE;
CREATE CONSTRAINT rel_depends_on_element_id IF NOT EXISTS FOR ()-[r:DEPENDS_ON]-() REQUIRE r.elementId IS UNIQUE;
CREATE CONSTRAINT rel_connects_via_element_id IF NOT EXISTS FOR ()-[r:CONNECTS_VIA]-() REQUIRE r.elementId IS UNIQUE;
CREATE CONSTRAINT rel_has_tag_element_id IF NOT EXISTS FOR ()-[r:HAS_TAG]-() REQUIRE r.elementId IS UNIQUE;

CREATE INDEX asset_kind IF NOT EXISTS FOR (n:Asset) ON (n.kind);
CREATE INDEX asset_name IF NOT EXISTS FOR (n:Asset) ON (n.name);
CREATE INDEX asset_host IF NOT EXISTS FOR (n:Asset) ON (n.host);
CREATE INDEX asset_deleted IF NOT EXISTS FOR (n:Asset) ON (n.deleted);
CREATE INDEX asset_enabled IF NOT EXISTS FOR (n:Asset) ON (n.enabled);
CREATE INDEX tag_name IF NOT EXISTS FOR (n:Tag) ON (n.name);
CREATE INDEX cluster_name IF NOT EXISTS FOR (n:Cluster) ON (n.name);
CREATE INDEX connects_via_order IF NOT EXISTS FOR ()-[r:CONNECTS_VIA]-() ON (r.order);
CREATE INDEX member_of_primary IF NOT EXISTS FOR ()-[r:MEMBER_OF]-() ON (r.primary);
