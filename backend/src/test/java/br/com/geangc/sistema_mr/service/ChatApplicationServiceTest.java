package br.com.geangc.sistema_mr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.geangc.sistema_mr.agent.model.AgentRunResult;
import br.com.geangc.sistema_mr.agent.model.AgentRunStatus;
import br.com.geangc.sistema_mr.agent.tool.AgentToolRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;

class ChatApplicationServiceTest {

    private final ConversationScopeService conversationScopeService = mock(ConversationScopeService.class);
    private final GroundingContextService groundingContextService = mock(GroundingContextService.class);
    private final InteractionService interactionService = mock(InteractionService.class);
    private final br.com.geangc.sistema_mr.agent.service.AgentRuntime agentRuntime = mock(
            br.com.geangc.sistema_mr.agent.service.AgentRuntime.class);
    private final ChatMemory chatMemory = mock(ChatMemory.class);
    private final AgentToolRegistry toolRegistry = mock(AgentToolRegistry.class);
    private final ChatApplicationService service = new ChatApplicationService(
            conversationScopeService,
            groundingContextService,
            interactionService,
            agentRuntime,
            chatMemory,
            toolRegistry,
            "Instrução de sistema"
    );

    @BeforeEach
    void setUp() {
        UUID subjectId = UUID.randomUUID();
        when(conversationScopeService.resolve("owner", null))
                .thenReturn(new ConversationScopeService.ConversationScope("chat-owner", subjectId, "Chat geral"));
        when(groundingContextService.prepare("chat-owner", "owner", "Preciso de um dado", List.of(), false))
                .thenReturn(new GroundingContextService.PreparedPrompt("Preciso de um dado", List.of()));
        when(chatMemory.get("chat-owner")).thenReturn(List.of());
        when(toolRegistry.all()).thenReturn(List.of());
    }

    @Test
    void persistsNaturalLanguageMessageWhenRunWaitsForUser() {
        Instant responseAt = Instant.parse("2026-09-06T18:00:00Z");
        UUID runId = UUID.randomUUID();
        when(agentRuntime.execute(any())).thenReturn(new AgentRunResult(
                runId,
                AgentRunStatus.WAITING_FOR_USER,
                "Posso agendar essa ação?",
                responseAt,
                "provider",
                "model",
                "USER_CONFIRMATION_REQUIRED"
        ));

        ChatApplicationService.ChatResult result = service.chat(
                "Preciso de um dado",
                List.of(),
                false,
                null,
                "owner"
        );

        assertEquals("Posso agendar essa ação?", result.content());
        assertEquals("ASSISTANT", result.messageType());
        verify(interactionService).persistCompleted(
                any(UUID.class),
                any(UUID.class),
                any(UUID.class),
                eq("Preciso de um dado"),
                eq("Posso agendar essa ação?"),
                eq("chat-owner"),
                eq("owner"),
                any(Instant.class),
                eq(responseAt),
                eq(List.of())
        );
    }
}
