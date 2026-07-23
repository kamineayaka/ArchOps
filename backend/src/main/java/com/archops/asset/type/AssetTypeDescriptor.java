package com.archops.asset.type;

/**
 * Public listing DTO for registered asset types (frontend discovery).
 */
public record AssetTypeDescriptor(String type, int defaultPort, String policyKind) {

    public static AssetTypeDescriptor from(AssetTypeHandler handler) {
        return new AssetTypeDescriptor(handler.type(), handler.defaultPort(), handler.policyKind());
    }
}
