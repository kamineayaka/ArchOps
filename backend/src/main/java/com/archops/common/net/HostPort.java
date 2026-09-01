package com.archops.common.net;

public record HostPort(String host, int port) {
    public static HostPort parse(String address) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Address is blank");
        }
        int split = address.lastIndexOf(':');
        if (split <= 0 || split == address.length() - 1) {
            throw new IllegalArgumentException("Address must be host:port: " + address);
        }
        return new HostPort(address.substring(0, split), Integer.parseInt(address.substring(split + 1)));
    }
}
