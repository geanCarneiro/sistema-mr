package br.com.geangc.sistema_mr.agent.state.model;

import br.com.geangc.sistema_mr.agent.state.DynamicPayload;
import java.util.Map;

public record StateProjection(long version, Map<String, Object> values) {
    public StateProjection {
        values = DynamicPayload.immutableCopy(values);
    }
}
