package com.archops.asset.domain;

public enum AssetKind {
    SERVER,
    CLUSTER,
    SERVICE,
    NETWORK,
    DATABASE,
    /** Kubernetes API / jump+kubectl (W4b). */
    K8S,
    /** Redis (W4c). */
    REDIS
}
