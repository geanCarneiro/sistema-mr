package br.com.geangc.sistema_mr.state.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DynamicPayloadOperations {

    private DynamicPayloadOperations() {}

    static Map<String, Object> apply(Map<String, Object> original, Map<String, Object> set, Set<String> remove) {
        Map<String, Object> result = copyMap(original);
        set.forEach((path, value) -> put(result, path, copyValue(value)));
        remove.forEach(path -> remove(result, path));
        return result;
    }

    static Map<String, Object> project(Map<String, Object> original, Collection<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return copyMap(original);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (String path : paths) {
            Object value = get(original, path);
            if (value != Missing.VALUE) {
                put(result, path, copyValue(value));
            }
        }
        return result;
    }

    static Map<String, Object> copyMap(Map<String, Object> original) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (original != null) {
            original.forEach((key, value) -> result.put(key, copyValue(value)));
        }
        return result;
    }

    private static void put(Map<String, Object> root, String path, Object value) {
        List<String> segments = segments(path);
        Map<String, Object> current = root;
        for (int index = 0; index < segments.size() - 1; index++) {
            String segment = segments.get(index);
            Object child = current.get(segment);
            if (!(child instanceof Map<?, ?>)) {
                child = new LinkedHashMap<String, Object>();
                current.put(segment, child);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> childMap = (Map<String, Object>) child;
            current = childMap;
        }
        current.put(segments.get(segments.size() - 1), value);
    }

    private static void remove(Map<String, Object> root, String path) {
        List<String> segments = segments(path);
        remove(root, segments, 0);
    }

    private static boolean remove(Map<String, Object> current, List<String> segments, int index) {
        String segment = segments.get(index);
        if (index == segments.size() - 1) {
            current.remove(segment);
            return current.isEmpty();
        }
        Object child = current.get(segment);
        if (child instanceof Map<?, ?> childMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nested = (Map<String, Object>) childMap;
            if (remove(nested, segments, index + 1)) {
                current.remove(segment);
            }
        }
        return current.isEmpty();
    }

    private static Object get(Map<String, Object> root, String path) {
        Object current = root;
        for (String segment : segments(path)) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(segment)) {
                return Missing.VALUE;
            }
            current = map.get(segment);
        }
        return current;
    }

    private static List<String> segments(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("O caminho do payload é obrigatório");
        }
        if (path.startsWith("/")) {
            return java.util.Arrays.stream(path.substring(1).split("/", -1))
                    .map(segment -> segment.replace("~1", "/").replace("~0", "~"))
                    .filter(segment -> !segment.isBlank())
                    .toList();
        }
        return List.of(path.split("\\."));
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, child) -> copy.put(String.valueOf(key), copyValue(child)));
            return copy;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(DynamicPayloadOperations::copyValue).toList();
        }
        if (value instanceof Set<?> set) {
            return new LinkedHashSet<>(set);
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        return value;
    }

    private enum Missing {
        VALUE
    }
}
