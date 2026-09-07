package br.com.geangc.sistema_mr.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.geangc.sistema_mr.agent.gateway.ModelGateway;
import br.com.geangc.sistema_mr.agent.gateway.ModelCapacityException;
import br.com.geangc.sistema_mr.agent.gateway.ModelRequest;
import br.com.geangc.sistema_mr.agent.gateway.ModelResponse;
import br.com.geangc.sistema_mr.agent.gateway.ToolExecutionPort;
import br.com.geangc.sistema_mr.agent.context.ContextSnapshot;
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
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;

class AgentRuntimePolicyTest {

    @Test
    void mountsSnapshotAndBackendScopeInEachModelRequest() {
        UUID runId = UUID.randomUUID();
        AgentRunRepository repository = mock(AgentRunRepository.class);
        ModelGateway modelGateway = mock(ModelGateway.class);
        ToolExecutionPort toolExecutionPort = mock(ToolExecutionPort.class);
        InMemoryRunScheduler scheduler = mock(InMemoryRunScheduler.class);
        br.com.geangc.sistema_mr.agent.context.ContextSnapshotProvider snapshotProvider = mock(
                br.com.geangc.sistema_mr.agent.context.ContextSnapshotProvider.class);
        when(repository.claim(runId, "owner")).thenReturn(true);
        when(snapshotProvider.snapshot(any(), eq("chat-owner"), eq("owner"), eq("Mensagem")))
                .thenReturn(new ContextSnapshot(null, "chat-owner", List.of(), List.of(), List.of()));
        when(modelGateway.invoke(any())).thenReturn(textModelResponse("Resposta"));

        AgentRuntime runtime = new AgentRuntime(
                properties(), repository, modelGateway, toolExecutionPort, scheduler,
                new ToolAutonomyPolicy(), snapshotProvider);
        AgentRunResult result = runtime.execute(command(runId));

        assertEquals(AgentRunStatus.COMPLETED, result.status());
        org.mockito.ArgumentCaptor<ModelRequest> request = org.mockito.ArgumentCaptor.forClass(ModelRequest.class);
        verify(modelGateway).invoke(request.capture());
        assertEquals("owner", request.getValue().toolContext().get("ownerSubject"));
        assertEquals("chat-owner", request.getValue().toolContext().get("conversationId"));
        assertEquals(1, request.getValue().messages().stream()
                .filter(message -> message instanceof SystemMessage).count());
    }

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

    @Test
    void continuesAfterToolCallAndRecordsEachModelOutcome() {
        UUID runId = UUID.randomUUID();
        AgentRunRepository repository = mock(AgentRunRepository.class);
        ModelGateway modelGateway = mock(ModelGateway.class);
        ToolExecutionPort toolExecutionPort = mock(ToolExecutionPort.class);
        InMemoryRunScheduler scheduler = mock(InMemoryRunScheduler.class);
        when(repository.claim(runId, "owner")).thenReturn(true);
        when(repository.reserveToolCall(eq(runId), eq("owner"), eq("call-1"), eq("lookup"), any()))
                .thenReturn(true);
        when(modelGateway.invoke(any())).thenReturn(
                modelResponse(new AssistantMessage.ToolCall("call-1", "function", "lookup", "{\"query\":\"teste\"}")),
                textModelResponse("Resultado final")
        );
        when(toolExecutionPort.execute(any(), any())).thenReturn(
                new ToolExecutionPort.ToolExecutionResult(
                        List.of(new org.springframework.ai.chat.messages.UserMessage("Resultado da ferramenta")),
                        false
                )
        );

        AgentRuntime runtime = new AgentRuntime(
                properties(), repository, modelGateway, toolExecutionPort, scheduler,
                new ToolAutonomyPolicy()
        );
        AgentRunResult result = runtime.execute(command(runId, tool("lookup", AutonomyLevel.EXECUTE)));

        assertEquals(AgentRunStatus.COMPLETED, result.status());
        assertEquals("Resultado final", result.content());
        verify(modelGateway, times(2)).invoke(any());
        verify(repository, times(2)).recordInvocation(
                eq(runId), eq("owner"), any(Integer.class), eq("route"), eq("provider"), eq("model"),
                eq("test"), any(Boolean.class), any(ModelResponse.InvocationUsage.class), any(String.class),
                any(), any()
        );
        verify(repository).recordInvocation(
                eq(runId), eq("owner"), eq(1), eq("route"), eq("provider"), eq("model"),
                eq("test"), eq(true), any(ModelResponse.InvocationUsage.class), eq("TOOL_CALLS"), any(), any()
        );
        verify(repository).recordInvocation(
                eq(runId), eq("owner"), eq(2), eq("route"), eq("provider"), eq("model"),
                eq("test"), eq(false), any(ModelResponse.InvocationUsage.class), eq("FINAL_RESPONSE"), any(), any()
        );
    }

    @Test
    void waitsForCapacityAndSchedulesRunForLaterRetry() {
        UUID runId = UUID.randomUUID();
        AgentRunRepository repository = mock(AgentRunRepository.class);
        ModelGateway modelGateway = mock(ModelGateway.class);
        ToolExecutionPort toolExecutionPort = mock(ToolExecutionPort.class);
        InMemoryRunScheduler scheduler = mock(InMemoryRunScheduler.class);
        when(repository.claim(runId, "owner")).thenReturn(true);
        when(modelGateway.invoke(any())).thenThrow(new ModelCapacityException("quota temporariamente esgotada", null));

        AgentRuntime runtime = new AgentRuntime(
                properties(), repository, modelGateway, toolExecutionPort, scheduler,
                new ToolAutonomyPolicy()
        );
        AgentRunResult result = runtime.execute(command(runId));

        assertEquals(AgentRunStatus.WAITING_FOR_CAPACITY, result.status());
        assertEquals("quota temporariamente esgotada", result.failureReason());
        verify(repository).waitForCapacity(runId, "owner", "quota temporariamente esgotada");
        verify(scheduler).enqueue(runId);
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

    private static ModelResponse textModelResponse(String content) {
        AssistantMessage assistantMessage = AssistantMessage.builder().content(content).build();
        return new ModelResponse(
                "route", "provider", "model", "test", mock(Prompt.class),
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
