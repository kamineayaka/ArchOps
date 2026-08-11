package com.archops.user.dto;

import com.archops.user.domain.PlatformRole;

public record CurrentUserResponse(
        String userId,
        String displayName,
        PlatformRole role,
        String roleLabel
) {
    public static CurrentUserResponse from(String userId, String displayName, PlatformRole role) {
        return new CurrentUserResponse(userId, displayName, role, labelOf(role));
    }

    private static String labelOf(PlatformRole role) {
        return switch (role) {
            case SENIOR -> "高级角色";
            case GENERAL -> "一般角色";
        };
    }
}
