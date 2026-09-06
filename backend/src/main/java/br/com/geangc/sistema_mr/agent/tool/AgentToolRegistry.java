package br.com.geangc.sistema_mr.agent.tool;

import br.com.geangc.sistema_mr.tool_calling.PythonToolConfig;
import br.com.geangc.sistema_mr.memory.tool.SemanticMemoryToolConfig;
import java.time.Duration;
import java.util.List;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

@Component
public class AgentToolRegistry {

    private final List<AgentTool> tools;

    public AgentToolRegistry(PythonToolConfig pythonToolConfig, SemanticMemoryToolConfig memoryToolConfig) {
        ToolCallback[] callbacks = ToolCallbacks.from(pythonToolConfig, memoryToolConfig);
        this.tools = java.util.Arrays.stream(callbacks).map(this::toAgentTool).toList();
    }

    public List<AgentTool> all() {
        return tools;
    }

    private AgentTool toAgentTool(ToolCallback callback) {
        var definition = callback.getToolDefinition();
        boolean memoryRead = "searchSemanticMemory".equals(definition.name());
        return new AgentTool(
                definition.name(), "1", definition.description(), definition.inputSchema(),
                memoryRead || "executePythonCode".equals(definition.name()) ? ToolRisk.LOW : ToolRisk.MEDIUM,
                memoryRead ? AutonomyLevel.OBSERVE : AutonomyLevel.EXECUTE,
                java.util.Set.of(), Duration.ofSeconds(memoryRead ? 5 : 12),
                memoryRead ? 4 : 2, true, callback);
    }
}
