package com.inventory.management.util;

import java.util.UUID;

public final class BusinessCodeGenerator {

    private BusinessCodeGenerator() {
    }

    public static String temporary() {
        return "TMP" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }

    public static String format(String prefix, Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid database ID");
        }

        return prefix + String.format("%03d", id);
    }
}