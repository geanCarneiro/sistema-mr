package br.com.geangc.sistema_mr.agent.tool;

import br.com.geangc.sistema_mr.tool_calling.PythonToolConfig;
import java.time.Duration;
import java.util.List;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

@Component
public class AgentToolRegistry {

    private final List<AgentTool> tools;

    public AgentToolRegistry(PythonToolConfig pythonToolConfig) {
        ToolCallback[] callbacks = ToolCallbacks.from(pythonToolConfig);
        if (callbacks.length != 1) {
            throw new IllegalStateException("A ferramenta Python deve registrar exatamente um callback");
        }
        ToolCallback pythonCallback = callbacks[0];
        var definition = pythonCallback.getToolDefinition();
        this.tools = List.of(new AgentTool(
                definition.name(),
                "1",
                definition.description(),
                definition.inputSchema(),
                ToolRisk.LOW,
                AutonomyLevel.EXECUTE,
                java.util.Set.of(),
                Duration.ofSeconds(12),
                1,
                true,
                pythonCallback
        ));
    }

    public List<AgentTool> all() {
        return tools;
    }
}
