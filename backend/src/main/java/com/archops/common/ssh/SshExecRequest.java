package com.archops.common.ssh;

import java.util.Map;

/**
 * Controlled SSH call bound to a curated physical host id (never an ad-hoc off-graph target).
 */
public record SshExecRequest(
        String hostId,
        String command,
        String action,
        int stepSeq,
        Map<String, String> context
) {
}
