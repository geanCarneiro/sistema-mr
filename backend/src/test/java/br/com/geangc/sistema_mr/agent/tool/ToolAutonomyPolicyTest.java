package br.com.geangc.sistema_mr.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.geangc.sistema_mr.agent.model.DataConstraints;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;

class ToolAutonomyPolicyTest {

    private static final ToolPolicyContext EXECUTABLE_CONTEXT = new ToolPolicyContext(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "owner",
            DataConstraints.unspecified(),
            AutonomyLevel.EXECUTE,
            Set.of("calculate")
    );

    private final ToolAutonomyPolicy policy = new ToolAutonomyPolicy();

    @Test
    void allowsToolWhenAutonomyAndPermissionsAreSatisfied() {
        AgentTool tool = tool("calculate", AutonomyLevel.EXECUTE, Set.of("calculate"));

        ToolDecision decision = policy.evaluate(
                tool,
                new ToolCallRequest("call-1", "calculate", "{\"value\": 2}"),
                EXECUTABLE_CONTEXT
        );

        assertEquals(ToolDecisionStatus.ALLOWED, decision.status());
    }

    @Test
    void asksForUserInputWhenToolArgumentsAreMissing() {
        AgentTool tool = tool("calculate", AutonomyLevel.EXECUTE, Set.of());

        ToolDecision decision = policy.evaluate(
                tool,
                new ToolCallRequest("call-1", "calculate", " "),
                EXECUTABLE_CONTEXT
        );

        assertEquals(ToolDecisionStatus.ASK, decision.status());
        assertEquals("USER_INPUT_REQUIRED", decision.reason());
    }

    @Test
    void asksForNaturalLanguageConfirmationBeforeConfirmedTool() {
        AgentTool tool = tool("sendMessage", AutonomyLevel.CONFIRM, Set.of());

        ToolDecision decision = policy.evaluate(
                tool,
                new ToolCallRequest("call-1", "sendMessage", "{\"recipient\":\"João\"}"),
                EXECUTABLE_CONTEXT
        );

        assertEquals(ToolDecisionStatus.CONFIRM, decision.status());
        assertEquals("USER_CONFIRMATION_REQUIRED", decision.reason());
        org.junit.jupiter.api.Assertions.assertTrue(decision.userMessage().contains("sendMessage"));
    }

    @Test
    void blocksToolWhenPermissionIsMissing() {
        AgentTool tool = tool("sendMessage", AutonomyLevel.EXECUTE, Set.of("messages:send"));

        ToolDecision decision = policy.evaluate(
                tool,
                new ToolCallRequest("call-1", "sendMessage", "{\"text\":\"Olá\"}"),
                EXECUTABLE_CONTEXT
        );

        assertEquals(ToolDecisionStatus.BLOCKED, decision.status());
        assertEquals("TOOL_PERMISSION_REQUIRED", decision.reason());
    }

    @Test
    void blocksToolWhenGrantedAutonomyIsInsufficient() {
        AgentTool tool = tool("scheduleReminder", AutonomyLevel.SCHEDULE, Set.of());
        ToolPolicyContext observeOnly = new ToolPolicyContext(
                EXECUTABLE_CONTEXT.runId(),
                EXECUTABLE_CONTEXT.subjectId(),
                EXECUTABLE_CONTEXT.ownerSubject(),
                EXECUTABLE_CONTEXT.dataConstraints(),
                AutonomyLevel.OBSERVE,
                Set.of()
        );

        ToolDecision decision = policy.evaluate(
                tool,
                new ToolCallRequest("call-1", "scheduleReminder", "{\"when\":\"tomorrow\"}"),
                observeOnly
        );

        assertEquals(ToolDecisionStatus.BLOCKED, decision.status());
        assertEquals("TOOL_AUTONOMY_LEVEL_NOT_ALLOWED", decision.reason());
    }

    @Test
    void blocksUnknownToolWithoutExecutingAnything() {
        ToolDecision decision = policy.evaluate(
                null,
                new ToolCallRequest("call-1", "unknown", "{}"),
                EXECUTABLE_CONTEXT
        );

        assertEquals(ToolDecisionStatus.BLOCKED, decision.status());
        assertEquals("TOOL_NOT_REGISTERED", decision.reason());
    }

    private static AgentTool tool(
            String name,
            AutonomyLevel autonomy,
            Set<String> permissions
    ) {
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(DefaultToolDefinition.builder()
                .name(name)
                .description("Executa " + name)
                .inputSchema("{}")
                .build());
        return new AgentTool(
                name,
                "1",
                "Executa " + name,
                "{}",
                ToolRisk.MEDIUM,
                autonomy,
                permissions,
                Duration.ofSeconds(5),
                1,
                false,
                callback
        );
    }
}
