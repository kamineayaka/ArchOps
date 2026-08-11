package com.archops.user.security;

/**
 * Temporary identity header for pre-JWT auth (backend-java rule / Spec story 35).
 */
public final class TempAuthHeaders {

    public static final String USER_ID = "X-ArchOps-User-Id";

    private TempAuthHeaders() {
    }
}
