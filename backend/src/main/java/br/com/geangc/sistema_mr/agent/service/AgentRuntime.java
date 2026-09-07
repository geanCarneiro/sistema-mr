package br.com.geangc.sistema_mr.agent.service;

import br.com.geangc.sistema_mr.agent.context.ContextSnapshot;
import br.com.geangc.sistema_mr.agent.context.ContextSnapshotProvider;
import br.com.geangc.sistema_mr.agent.gateway.ModelCapacityException;
import br.com.geangc.sistema_mr.agent.gateway.ModelGateway;
import br.com.geangc.sistema_mr.agent.gateway.ModelRequest;
import br.com.geangc.sistema_mr.agent.gateway.ModelResponse;
import br.com.geangc.sistema_mr.agent.gateway.ProviderSelectionPolicy;
import br.com.geangc.sistema_mr.agent.gateway.ToolExecutionPort;
import br.com.geangc.sistema_mr.agent.model.AgentRun;
import br.com.geangc.sistema_mr.agent.model.AgentRunCommand;
import br.com.geangc.sistema_mr.agent.model.AgentRunResult;
import br.com.geangc.sistema_mr.agent.model.AgentRunStatus;
import br.com.geangc.sistema_mr.agent.tool.AgentTool;
import br.com.geangc.sistema_mr.agent.tool.ToolAutonomyPolicy;
import br.com.geangc.sistema_mr.agent.tool.ToolCallRequest;
import br.com.geangc.sistema_mr.agent.tool.ToolDecision;
import br.com.geangc.sistema_mr.agent.tool.ToolDecisionStatus;
import br.com.geangc.sistema_mr.agent.tool.ToolPolicyContext;
import br.com.geangc.sistema_mr.configuration.AgentRuntimeProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AgentRuntime {

    private final AgentRuntimeProperties properties;
    private final br.com.geangc.sistema_mr.agent.repository.AgentRunRepository repository;
    private final ModelGateway modelGateway;
    private final ToolExecutionPort toolExecutionPort;
    private final InMemoryRunScheduler scheduler;
    private final ToolAutonomyPolicy toolAutonomyPolicy;
    private final ContextSnapshotProvider contextSnapshotProvider;

    public AgentRuntime(
            AgentRuntimeProperties properties,
            br.com.geangc.sistema_mr.agent.repository.AgentRunRepository repository,
            ModelGateway modelGateway,
            ToolExecutionPort toolExecutionPort,
            InMemoryRunScheduler scheduler,
            ToolAutonomyPolicy toolAutonomyPolicy
    ) {
        this(properties, repository, modelGateway, toolExecutionPort, scheduler, toolAutonomyPolicy,
                ContextSnapshotProvider.empty());
    }

    @Autowired
    public AgentRuntime(
            AgentRuntimeProperties properties,
            br.com.geangc.sistema_mr.agent.repository.AgentRunRepository repository,
            ModelGateway modelGateway,
            ToolExecutionPort toolExecutionPort,
            InMemoryRunScheduler scheduler,
            ToolAutonomyPolicy toolAutonomyPolicy,
            ContextSnapshotProvider contextSnapshotProvider
    ) {
        this.properties = properties;
        this.repository = repository;
        this.modelGateway = modelGateway;
        this.toolExecutionPort = toolExecutionPort;
        this.scheduler = scheduler;
        this.toolAutonomyPolicy = toolAutonomyPolicy;
        this.contextSnapshotProvider = contextSnapshotProvider;
    }

    public AgentRunResult execute(AgentRunCommand command) {
        Instant createdAt = Instant.now();
        AgentRun run = new AgentRun(
                command.runId(),
                command.subjectId(),
                command.conversationId(),
                command.ownerSubject(),
                command.trigger(),
                AgentRunStatus.SCHEDULED,
                0,
                0,
                0,
                0,
                createdAt,
                null,
                null,
                null,
                null
        );
        repository.create(run, command.subjectTitle());

        if (!repository.claim(run.id(), run.ownerSubject())) {
            repository.waitForCapacity(run.id(), run.ownerSubject(), "O assunto já possui uma execução ativa");
            scheduler.enqueue(run.id());
            throw new AgentRunUnavailableException("O assunto já possui uma execução em andamento");
        }

        ContextSnapshot snapshot = contextSnapshotProvider.snapshot(
                command.subjectId(), command.conversationId(), command.ownerSubject(), latestUserPrompt(command.messages()));
        List<Message> conversation = new ArrayList<>(command.messages());
        conversation.add(Math.min(1, conversation.size()),
                new org.springframework.ai.chat.messages.SystemMessage(snapshot.asModelText()));
        Instant deadline = createdAt.plus(Duration.ofSeconds(properties.limits().maxDurationSeconds()));
        int modelInvocations = 0;
        int toolCalls = 0;
        Map<String, Integer> callsPerTool = new HashMap<>();
        Map<String, AgentTool> toolsByName = command.tools().stream()
                .collect(java.util.stream.Collectors.toMap(AgentTool::name, tool -> tool, (first, second) -> first));
        ToolPolicyContext toolPolicyContext = new ToolPolicyContext(
                run.id(),
                run.subjectId(),
                run.ownerSubject(),
                command.dataConstraints(),
                command.grantedAutonomy(),
                command.permissions()
        );
        String providerId = null;
        String modelId = null;

        try {
            while (true) {
                if (Instant.now().isAfter(deadline)) {
                    return fail(run, "RUN_TIME_LIMIT_EXCEEDED");
                }
                if (modelInvocations >= properties.limits().maxModelInvocations()
                        || modelInvocations >= properties.limits().maxSteps()) {
                    return fail(run, "RUN_MODEL_INVOCATION_LIMIT_EXCEEDED");
                }

                boolean localOnly = command.dataConstraints().localOnly();
                String routeId = localOnly
                        ? properties.localRouteConfig().id()
                        : properties.defaultRouteConfig().id();
                ModelRequest request = new ModelRequest(
                        run.id(),
                        routeId,
                        conversation,
                        command.tools().stream().map(AgentTool::callback).toList(),
                        toolContext(command),
                        command.dataConstraints(),
                        localOnly ? ProviderSelectionPolicy.LOCAL_ONLY : ProviderSelectionPolicy.AUTO,
                        properties.limits().maxSteps() - modelInvocations,
                        properties.limits().maxModelInvocations() - modelInvocations,
                        properties.limits().maxToolCalls() - toolCalls
                );
                Instant invocationStartedAt = Instant.now();
                ModelResponse modelResponse;
                try {
                    modelResponse = modelGateway.invoke(request);
                } catch (ModelCapacityException exception) {
                    repository.waitForCapacity(run.id(), run.ownerSubject(), exception.getMessage());
                    scheduler.enqueue(run.id());
                    return new AgentRunResult(
                            run.id(),
                            AgentRunStatus.WAITING_FOR_CAPACITY,
                            null,
                            null,
                            providerId,
                            modelId,
                            exception.getMessage()
                    );
                }
                Instant invocationCompletedAt = Instant.now();
                modelInvocations++;
                providerId = modelResponse.providerId();
                modelId = modelResponse.modelId();
                ChatResponse response = modelResponse.response();

                if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                    return fail(run, "MODEL_RETURNED_NO_RESULT");
                }

                repository.recordInvocation(
                        run.id(),
                        run.ownerSubject(),
                        modelInvocations,
                        modelResponse.routeId(),
                        modelResponse.providerId(),
                        modelResponse.modelId(),
                        modelResponse.selectionReason(),
                        modelResponse.hasToolCalls(),
                        invocationStartedAt,
                        invocationCompletedAt
                );

                AssistantMessage assistantMessage = response.getResult().getOutput();
                if (!assistantMessage.hasToolCalls()) {
                    String content = assistantMessage.getText();
                    if (content == null || content.isBlank()) {
                        return fail(run, "MODEL_RETURNED_EMPTY_RESPONSE");
                    }
                    repository.complete(run.id(), run.ownerSubject(), content, invocationCompletedAt);
                    return new AgentRunResult(
                            run.id(),
                            AgentRunStatus.COMPLETED,
                            content,
                            invocationCompletedAt,
                            providerId,
                            modelId,
                            null
                    );
                }

                int requestedToolCalls = assistantMessage.getToolCalls().size();
                if (toolCalls + requestedToolCalls > properties.limits().maxToolCalls()) {
                    return fail(run, "RUN_TOOL_CALL_LIMIT_EXCEEDED");
                }

                Map<String, Integer> requestedByTool = new HashMap<>();
                for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                    AgentTool tool = toolsByName.get(toolCall.name());
                    ToolDecision decision = toolAutonomyPolicy.evaluate(
                            tool,
                            new ToolCallRequest(toolCall.id(), toolCall.name(), toolCall.arguments()),
                            toolPolicyContext
                    );
                    if (decision.status() != ToolDecisionStatus.ALLOWED) {
                        if (decision.status() == ToolDecisionStatus.ASK
                                || decision.status() == ToolDecisionStatus.CONFIRM) {
                            repository.waitForUser(
                                    run.id(),
                                    run.ownerSubject(),
                                    decision.status().name(),
                                    decision.userMessage(),
                                    toolCall.id(),
                                    toolCall.name(),
                                    toolCall.arguments()
                            );
                            return new AgentRunResult(
                                    run.id(),
                                    AgentRunStatus.WAITING_FOR_USER,
                                    decision.userMessage(),
                                    Instant.now(),
                                    providerId,
                                    modelId,
                                    decision.reason()
                            );
                        }
                        repository.recordToolCall(
                                run.id(),
                                run.ownerSubject(),
                                toolCall.id(),
                                toolCall.name(),
                                toolCall.arguments(),
                                decision.reason(),
                                false
                        );
                        return fail(run, decision.reason());
                    }
                    int currentCalls = callsPerTool.getOrDefault(tool.name(), 0);
                    int requestedCalls = requestedByTool.merge(tool.name(), 1, Integer::sum);
                    if (currentCalls + requestedCalls > tool.maxCallsPerRun()) {
                        return fail(run, "TOOL_CALL_LIMIT_EXCEEDED:" + tool.name());
                    }
                    boolean reserved = repository.reserveToolCall(
                            run.id(),
                            run.ownerSubject(),
                            toolCall.id(),
                            toolCall.name(),
                            toolCall.arguments()
                    );
                    if (!reserved) {
                        return fail(run, "TOOL_CALL_ALREADY_EXECUTED_OR_IN_PROGRESS");
                    }
                }

                ToolExecutionPort.ToolExecutionResult executionResult;
                try {
                    executionResult = toolExecutionPort.execute(modelResponse.prompt(), response);
                } catch (RuntimeException exception) {
                    recordFailedToolCalls(run, assistantMessage, exception);
                    return fail(run, "TOOL_EXECUTION_FAILED");
                }

                toolCalls += requestedToolCalls;
                for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                    callsPerTool.merge(toolCall.name(), 1, Integer::sum);
                }
                recordToolResponses(run, executionResult.conversationHistory());
                conversation = new ArrayList<>(executionResult.conversationHistory());

                if (executionResult.returnDirect()) {
                    String content = lastToolResponse(executionResult.conversationHistory());
                    repository.complete(run.id(), run.ownerSubject(), content, Instant.now());
                    return new AgentRunResult(
                            run.id(),
                            AgentRunStatus.COMPLETED,
                            content,
                            Instant.now(),
                            providerId,
                            modelId,
                            null
                    );
                }
            }
        } catch (RuntimeException exception) {
            repository.fail(run.id(), run.ownerSubject(), exception.getMessage(), Instant.now());
            throw exception;
        }
    }

    private static Map<String, Object> toolContext(AgentRunCommand command) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("runId", command.runId().toString());
        context.put("subjectId", command.subjectId().toString());
        context.put("conversationId", command.conversationId());
        context.put("ownerSubject", command.ownerSubject());
        return context;
    }

    private static String latestUserPrompt(List<Message> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            if (messages.get(index) instanceof UserMessage userMessage) {
                return userMessage.getText();
            }
        }
        return "";
    }

    private AgentRunResult fail(AgentRun run, String reason) {
        Instant completedAt = Instant.now();
        repository.fail(run.id(), run.ownerSubject(), reason, completedAt);
        return new AgentRunResult(
                run.id(),
                AgentRunStatus.FAILED,
                null,
                completedAt,
                null,
                null,
                reason
        );
    }

    private void recordToolResponses(AgentRun run, List<Message> messages) {
        for (Message message : messages) {
            if (!(message instanceof ToolResponseMessage toolResponseMessage)) {
                continue;
            }
            for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                repository.recordToolCall(
                        run.id(),
                        run.ownerSubject(),
                        response.id(),
                        response.name(),
                        "",
                        response.responseData(),
                        true
                );
            }
        }
    }

    private void recordFailedToolCalls(AgentRun run, AssistantMessage assistantMessage, RuntimeException exception) {
        for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
            repository.recordToolCall(
                    run.id(),
                    run.ownerSubject(),
                    toolCall.id(),
                    toolCall.name(),
                    toolCall.arguments(),
                    exception.getMessage(),
                    false
            );
        }
    }

    private static String lastToolResponse(List<Message> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            if (message instanceof ToolResponseMessage responseMessage
                    && !responseMessage.getResponses().isEmpty()) {
                return responseMessage.getResponses().get(responseMessage.getResponses().size() - 1).responseData();
            }
        }
        return "A ferramenta concluiu a execução sem retornar conteúdo textual.";
    }
}
