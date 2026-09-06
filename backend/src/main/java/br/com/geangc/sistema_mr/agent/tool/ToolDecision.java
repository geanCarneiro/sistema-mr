package br.com.geangc.sistema_mr.agent.tool;

public record ToolDecision(
        ToolDecisionStatus status,
        String userMessage,
        String reason
) {
    public static ToolDecision allowed() {
        return new ToolDecision(ToolDecisionStatus.ALLOWED, null, null);
    }

    public static ToolDecision ask(String message) {
        return new ToolDecision(ToolDecisionStatus.ASK, message, "USER_INPUT_REQUIRED");
    }

    public static ToolDecision confirm(String message) {
        return new ToolDecision(ToolDecisionStatus.CONFIRM, message, "USER_CONFIRMATION_REQUIRED");
    }

    public static ToolDecision blocked(String reason) {
        return new ToolDecision(ToolDecisionStatus.BLOCKED, null, reason);
    }
}
