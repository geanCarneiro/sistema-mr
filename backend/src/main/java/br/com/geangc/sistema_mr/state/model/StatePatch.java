package br.com.geangc.sistema_mr.state.model;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public record StatePatch(Map<String, Object> set, Set<String> remove) {
    public StatePatch {
        set = set == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(set));
        remove = remove == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(remove));
        if (set.keySet().stream().anyMatch(path -> path == null || path.isBlank())
                || remove.stream().anyMatch(path -> path == null || path.isBlank())) {
            throw new IllegalArgumentException("Os caminhos do patch são obrigatórios");
        }
        if (set.keySet().stream().anyMatch(remove::contains)) {
            throw new IllegalArgumentException("Um caminho não pode ser definido e removido no mesmo patch");
        }
        if (set.isEmpty() && remove.isEmpty()) {
            throw new IllegalArgumentException("O patch deve alterar ao menos um caminho");
        }
    }
}
