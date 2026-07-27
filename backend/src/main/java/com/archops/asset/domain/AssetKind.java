package com.archops.asset.domain;

public enum AssetKind {
    SERVER,
    CLUSTER,
    SERVICE,
    NETWORK,
    DATABASE,
    /** Former asset_group — always backed by an assets row + Neo4j :Tag. */
    TAG,
    /** Logical environment / site node — always backed by an assets row. */
    ENVIRONMENT
}
