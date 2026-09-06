package br.com.geangc.sistema_mr.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.geangc.sistema_mr.agent.gateway.ModelGateway;
import br.com.geangc.sistema_mr.agent.gateway.ModelResponse;
import br.com.geangc.sistema_mr.agent.gateway.ToolExecutionPort;
import br.com.geangc.sistema_mr.agent.model.AgentRunCommand;
import br.com.geangc.sistema_mr.agent.model.AgentRunResult;
import br.com.geangc.sistema_mr.agent.model.AgentRunStatus;
import br.com.geangc.sistema_mr.agent.model.AgentRunTrigger;
import br.com.geangc.sistema_mr.agent.model.DataConstraints;
import br.com.geangc.sistema_mr.agent.repository.AgentRunRepository;
import br.com.geangc.sistema_mr.agent.tool.AgentTool;
import br.com.geangc.sistema_mr.agent.tool.AutonomyLevel;
import br.com.geangc.sistema_mr.agent.tool.ToolAutonomyPolicy;
import br.com.geangc.sistema_mr.agent.tool.ToolRisk;
import br.com.geangc.sistema_mr.configuration.AgentRuntimeProperties;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;

class AgentRuntimePolicyTest {

    @Test
    void returnsNaturalLanguageConfirmationWithoutExecutingTool() {
        UUID runId = UUID.randomUUID();
        AgentRunRepository repository = mock(AgentRunRepository.class);
        ModelGateway modelGateway = mock(ModelGateway.class);
        ToolExecutionPort toolExecutionPort = mock(ToolExecutionPort.class);
        InMemoryRunScheduler scheduler = mock(InMemoryRunScheduler.class);
        when(repository.claim(runId, "owner")).thenReturn(true);
        when(modelGateway.invoke(any())).thenReturn(modelResponse(
                new AssistantMessage.ToolCall("call-1", "function", "sendMessage", "{\"text\":\"Olá\"}")));

        AgentRuntime runtime = new AgentRuntime(
                properties(),
                repository,
                modelGateway,
                toolExecutionPort,
                scheduler,
                new ToolAutonomyPolicy()
        );
        AgentRunResult result = runtime.execute(command(runId, tool("sendMessage", AutonomyLevel.CONFIRM)));

        assertEquals(AgentRunStatus.WAITING_FOR_USER, result.status());
        assertEquals("USER_CONFIRMATION_REQUIRED", result.failureReason());
        verify(repository).waitForUser(
                eq(runId),
                eq("owner"),
                eq("CONFIRM"),
                any(String.class),
                eq("call-1"),
                eq("sendMessage"),
                eq("{\"text\":\"Olá\"}")
        );
        verify(toolExecutionPort, never()).execute(any(), any());
    }

    @Test
    void blocksToolWithInsufficientAutonomyBeforeReservation() {
        UUID runId = UUID.randomUUID();
        AgentRunRepository repository = mock(AgentRunRepository.class);
        ModelGateway modelGateway = mock(ModelGateway.class);
        ToolExecutionPort toolExecutionPort = mock(ToolExecutionPort.class);
        InMemoryRunScheduler scheduler = mock(InMemoryRunScheduler.class);
        when(repository.claim(runId, "owner")).thenReturn(true);
        when(modelGateway.invoke(any())).thenReturn(modelResponse(
                new AssistantMessage.ToolCall("call-1", "function", "scheduleReminder", "{\"when\":\"tomorrow\"}")));

        AgentRuntime runtime = new AgentRuntime(
                properties(),
                repository,
                modelGateway,
                toolExecutionPort,
                scheduler,
                new ToolAutonomyPolicy()
        );
        AgentRunResult result = runtime.execute(command(
                runId,
                tool("scheduleReminder", AutonomyLevel.SCHEDULE),
                AutonomyLevel.OBSERVE
        ));

        assertEquals(AgentRunStatus.FAILED, result.status());
        assertEquals("TOOL_AUTONOMY_LEVEL_NOT_ALLOWED", result.failureReason());
        verify(repository, never()).reserveToolCall(any(), any(), any(), any(), any());
        verify(toolExecutionPort, never()).execute(any(), any());
    }

    private static AgentRunCommand command(UUID runId, AgentTool... tools) {
        return command(runId, List.of(tools), AutonomyLevel.EXECUTE);
    }

    private static AgentRunCommand command(
            UUID runId,
            AgentTool tool,
            AutonomyLevel autonomy
    ) {
        return command(runId, List.of(tool), autonomy);
    }

    private static AgentRunCommand command(
            UUID runId,
            List<AgentTool> tools,
            AutonomyLevel autonomy
    ) {
        return new AgentRunCommand(
                runId,
                UUID.randomUUID(),
                "Chat geral",
                "chat-owner",
                "owner",
                AgentRunTrigger.USER_MESSAGE,
                List.of(new org.springframework.ai.chat.messages.UserMessage("Mensagem")),
                tools,
                DataConstraints.unspecified(),
                autonomy,
                Set.of()
        );
    }

    private static AgentRuntimeProperties properties() {
        return new AgentRuntimeProperties(
                "route",
                new AgentRuntimeProperties.Limits(3, 3, 3, 10, 1000, 100),
                List.of(new AgentRuntimeProperties.Route(
                        "route",
                        "provider",
                        "model",
                        true,
                        1000,
                        100,
                        new AgentRuntimeProperties.Capabilities(true, true, false),
                        new AgentRuntimeProperties.Quota(10, 1000, 100)
                ))
        );
    }

    private static ModelResponse modelResponse(AssistantMessage.ToolCall toolCall) {
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCall))
                .build();
        return new ModelResponse(
                "route",
                "provider",
                "model",
                "test",
                mock(Prompt.class),
                new ChatResponse(List.of(new Generation(assistantMessage)))
        );
    }

    private static AgentTool tool(String name, AutonomyLevel autonomy) {
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
                Set.of(),
                Duration.ofSeconds(5),
                1,
                false,
                callback
        );
    }
}
