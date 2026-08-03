package com.archops.asset.type;

/**
 * Public listing DTO for registered asset types (frontend discovery).
 * {@code connectAction} is lowercase to match the frontend registry contract.
 */
public record AssetTypeDescriptor(
        String type,
        int defaultPort,
        String policyKind,
        String connectAction,
        String authMode,
        boolean showHost,
        boolean showPort,
        boolean showDatabaseName,
        boolean supportsTest) {

    public static AssetTypeDescriptor from(AssetTypeHandler handler) {
        ConnectAction action = handler.connectAction() != null ? handler.connectAction() : ConnectAction.NONE;
        String authMode = switch (action) {
            case TERMINAL -> "ssh";
            case QUERY -> "password";
            default -> "none";
        };
        boolean hostPort = action == ConnectAction.TERMINAL
                || action == ConnectAction.QUERY
                || "CLUSTER".equals(handler.type())
                || "SERVICE".equals(handler.type());
        boolean database = action == ConnectAction.QUERY;
        boolean supportsTest = action == ConnectAction.TERMINAL || action == ConnectAction.QUERY;
        return new AssetTypeDescriptor(
                handler.type(),
                handler.defaultPort(),
                handler.policyKind(),
                action.name().toLowerCase(),
                authMode,
                hostPort && handler.defaultPort() >= 0,
                hostPort && handler.defaultPort() > 0,
                database,
                supportsTest);
    }
}
