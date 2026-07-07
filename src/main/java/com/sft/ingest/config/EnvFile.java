package com.sft.ingest.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal KEY=VALUE parser for .env-style files. No quoting/escaping beyond
 * stripping one layer of matching quotes — this project doesn't need more.
 */
public final class EnvFile {

    public static Map<String, String> parse(Path path) throws IOException {
        if (!Files.exists(path)) {
            return Map.of();
        }
        Map<String, String> values = new HashMap<>();
        for (String line : Files.readAllLines(path)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = trimmed.substring(0, eq).strip();
            String value = unquote(trimmed.substring(eq + 1).strip());
            values.put(key, value);
        }
        return Map.copyOf(values);
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private EnvFile() {}
}
