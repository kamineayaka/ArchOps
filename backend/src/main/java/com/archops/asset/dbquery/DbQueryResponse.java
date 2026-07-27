package com.archops.asset.dbquery;

import java.util.List;

public record DbQueryResponse(
        String status,
        Long approvalId,
        boolean mutating,
        String riskLevel,
        List<String> columns,
        List<List<Object>> rows,
        int rowCount,
        boolean truncated,
        long updateCount,
        Long elapsedMs,
        String message) {

    public static DbQueryResponse pending(Long approvalId, String riskLevel, String message) {
        return new DbQueryResponse(
                "PENDING_APPROVAL",
                approvalId,
                true,
                riskLevel,
                List.of(),
                List.of(),
                0,
                false,
                0L,
                null,
                message);
    }

    public static DbQueryResponse executed(
            boolean mutating,
            String riskLevel,
            List<String> columns,
            List<List<Object>> rows,
            int rowCount,
            boolean truncated,
            long updateCount,
            long elapsedMs,
            String message) {
        return new DbQueryResponse(
                "EXECUTED",
                null,
                mutating,
                riskLevel,
                columns,
                rows,
                rowCount,
                truncated,
                updateCount,
                elapsedMs,
                message);
    }
}
