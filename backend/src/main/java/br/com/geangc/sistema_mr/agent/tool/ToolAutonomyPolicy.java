package br.com.geangc.sistema_mr.agent.tool;

import org.springframework.stereotype.Component;

@Component
public class ToolAutonomyPolicy {

    public ToolDecision evaluate(
            AgentTool tool,
            ToolCallRequest request,
            ToolPolicyContext context
    ) {
        if (tool == null) {
            return ToolDecision.blocked("TOOL_NOT_REGISTERED");
        }
        if (request == null || request.name() == null || !tool.name().equals(request.name())) {
            return ToolDecision.blocked("TOOL_REQUEST_DOES_NOT_MATCH_REGISTRATION");
        }
        if (request.arguments() == null || request.arguments().isBlank()) {
            return ToolDecision.ask("Preciso de mais informações para executar essa ferramenta.");
        }
        if (!context.permissions().containsAll(tool.requiredPermissions())) {
            return ToolDecision.blocked("TOOL_PERMISSION_REQUIRED");
        }
        if (tool.requiredAutonomy() == AutonomyLevel.ASK) {
            return ToolDecision.ask("Preciso de uma informação adicional antes de continuar.");
        }
        if (tool.requiredAutonomy() == AutonomyLevel.CONFIRM) {
            return ToolDecision.confirm("Posso executar a ação solicitada por meio de "
                    + tool.description() + "?");
        }
        if (!context.grantedAutonomy().permits(tool.requiredAutonomy())) {
            return ToolDecision.blocked("TOOL_AUTONOMY_LEVEL_NOT_ALLOWED");
        }
        return ToolDecision.allowed();
    }
}
