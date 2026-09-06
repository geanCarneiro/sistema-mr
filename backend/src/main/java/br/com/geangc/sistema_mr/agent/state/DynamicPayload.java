package br.com.geangc.sistema_mr.agent.state;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.ObjectMapper;

public final class DynamicPayload {

    private DynamicPayload() {}

    public static String toJson(Map<String, Object> payload, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(canonicalize(payload == null ? Map.of() : payload));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("O payload não pôde ser serializado como JSON", exception);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(String json, ObjectMapper objectMapper) {
        try {
            Map<String, Object> payload = objectMapper.readValue(json, Map.class);
            return payload == null ? Map.of() : payload;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("O payload persistido não é um objeto JSON válido", exception);
        }
    }

    public static Object toMapValue(String json, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("A propriedade persistida não é um valor JSON válido", exception);
        }
    }

    public static Map<String, Object> immutableCopy(Map<String, Object> payload) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(payload == null ? Map.of() : payload));
    }

    public static Map<String, String> fields(Map<String, Object> payload, ObjectMapper objectMapper) {
        Map<String, String> fields = new LinkedHashMap<>();
        walk("", payload == null ? Map.of() : payload, fields, objectMapper);
        return fields;
    }

    private static Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> ordered = new TreeMap<>();
            map.forEach((key, child) -> ordered.put(String.valueOf(key), canonicalize(child)));
            return ordered;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(DynamicPayload::canonicalize).toList();
        }
        return value;
    }

    public static List<String> normalizePaths(Iterable<String> paths) {
        List<String> normalized = new ArrayList<>();
        if (paths == null) {
            return normalized;
        }
        for (String path : paths) {
            if (path == null || (!path.isEmpty() && !path.startsWith("/"))) {
                throw new IllegalArgumentException("Os caminhos do estado devem usar JSON Pointer");
            }
            normalized.add(path);
        }
        return List.copyOf(normalized);
    }

    public static String fingerprint(long expectedVersion, String payloadJson, String origin,
            double confidence, String reason, String idempotencyKey, String sourceType,
            String sourceId, String sourceVersion) {
        String value = expectedVersion + "|" + payloadJson + "|" + origin + "|" + confidence
                + "|" + reason + "|" + idempotencyKey + "|" + sourceType + "|" + sourceId + "|" + sourceVersion;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    private static void walk(String path, Object value, Map<String, String> fields, ObjectMapper objectMapper) {
        fields.put(path, toJsonValue(value, objectMapper));
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, child) -> walk(path + "/" + escape(String.valueOf(key)), child, fields, objectMapper));
        } else if (value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                walk(path + "/" + index, list.get(index), fields, objectMapper);
            }
        }
    }

    private static String toJsonValue(Object value, ObjectMapper objectMapper) {
        return toJsonValueInternal(value, objectMapper);
    }

    private static String toJsonValueInternal(Object value, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Uma propriedade do payload não pôde ser serializada", exception);
        }
    }

    private static String escape(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }
}
