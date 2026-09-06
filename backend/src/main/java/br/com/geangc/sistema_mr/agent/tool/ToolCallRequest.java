package br.com.geangc.sistema_mr.agent.tool;

public record ToolCallRequest(
        String id,
        String name,
        String arguments
) {
}
