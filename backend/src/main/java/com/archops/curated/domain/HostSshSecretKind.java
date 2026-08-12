package com.archops.curated.domain;

/**
 * Kind of SSH secret material stored for a graph-resident physical host.
 */
public enum HostSshSecretKind {
    PASSWORD,
    PRIVATE_KEY
}
