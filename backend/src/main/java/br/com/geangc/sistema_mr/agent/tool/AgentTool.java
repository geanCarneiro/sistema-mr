package br.com.geangc.sistema_mr.agent.tool;

import java.time.Duration;
import java.util.Set;
import org.springframework.ai.tool.ToolCallback;

public record AgentTool(
        String name,
        String version,
        String description,
        String inputSchema,
        ToolRisk risk,
        AutonomyLevel requiredAutonomy,
        Set<String> requiredPermissions,
        Duration timeout,
        int maxCallsPerRun,
        boolean localOnly,
        ToolCallback callback
) {
    public AgentTool {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("O nome da ferramenta é obrigatório");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("A versão da ferramenta é obrigatória");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("A descrição da ferramenta é obrigatória");
        }
        if (risk == null || requiredAutonomy == null || callback == null) {
            throw new IllegalArgumentException("A definição da ferramenta está incompleta");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("O timeout da ferramenta deve ser positivo");
        }
        if (maxCallsPerRun < 1) {
            throw new IllegalArgumentException("O limite de chamadas da ferramenta deve ser positivo");
        }
        requiredPermissions = requiredPermissions == null ? Set.of() : Set.copyOf(requiredPermissions);
        inputSchema = inputSchema == null ? "{}" : inputSchema;
    }
}
