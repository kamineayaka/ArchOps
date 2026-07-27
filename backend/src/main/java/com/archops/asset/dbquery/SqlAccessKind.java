package com.archops.asset.dbquery;

/** Whether a SQL statement mutates data / schema. */
public enum SqlAccessKind {
    READ,
    WRITE
}
