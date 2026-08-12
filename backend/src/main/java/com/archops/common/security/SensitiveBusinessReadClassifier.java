package com.archops.common.security;

/**
 * Minimal stub classifier for 敏感读业务数据 (ADR-0041 / Spec negative acceptance).
 */
public final class SensitiveBusinessReadClassifier {

    private SensitiveBusinessReadClassifier() {
    }

    public static boolean isSensitive(String target, String intent) {
        String haystack = ((target == null ? "" : target) + " " + (intent == null ? "" : intent)).toLowerCase();
        return containsAny(haystack,
                "order", "orders", "customer", "finance", "invoice", "payment",
                "业务库", "订单", "客户", "财务", "账单");
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
