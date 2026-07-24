package com.archops.asset.dto;

import java.util.List;

public record AssetQueryResponse(boolean ok, String message, List<String> columns, List<List<String>> rows) {
    public static AssetQueryResponse failure(String message) {
        return new AssetQueryResponse(false, message, List.of(), List.of());
    }

    public static AssetQueryResponse table(String message, List<String> columns, List<List<String>> rows) {
        return new AssetQueryResponse(true, message, columns, rows);
    }

    public static AssetQueryResponse text(String message) {
        return new AssetQueryResponse(true, message, List.of("result"), List.of(List.of(message)));
    }
}
