package com.archops.common.ssh;

import java.time.Instant;
import java.util.Map;

public record SshCallRecord(
        Instant at,
        String hostId,
        String command,
        String action,
        int stepSeq,
        Map<String, String> context,
        boolean success,
        int exitCode,
        String failureReason
) {
}
