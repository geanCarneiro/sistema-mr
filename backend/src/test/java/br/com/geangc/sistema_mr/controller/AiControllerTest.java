package br.com.geangc.sistema_mr.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import br.com.geangc.sistema_mr.service.ConversationScopeService;

class AiControllerTest {

    @Test
    void conversationIdIsDerivedFromAuthenticatedSubject() {
        ConversationScopeService service = new ConversationScopeService(null);
        String firstUser = service.resolve("google-subject-a").conversationId();
        String secondUser = service.resolve("google-subject-b").conversationId();

        assertEquals("chat-google-subject-a", firstUser);
        assertEquals("chat-google-subject-b", secondUser);
    }

    @Test
    void conversationIdRejectsMissingSubject() {
        ConversationScopeService service = new ConversationScopeService(null);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.resolve(" "));
    }

    @Test
    void sameUserConversationIsUniversalAcrossSubjectContexts() {
        ConversationScopeService service = new ConversationScopeService(null);
        var general = service.resolve("google-subject-a");
        var selected = service.resolve("google-subject-a", general.subjectId());

        assertEquals(general.conversationId(), selected.conversationId());
        assertEquals(general.subjectId(), selected.subjectId());
    }

    @Test
    void relatedFileSearchIsOptInAndBackwardCompatible() {
        var legacyRequest = new AiController.ChatRequestDTO("Pergunta", null, null);
        var hybridRequest = new AiController.ChatRequestDTO("Pergunta", null, true);

        assertFalse(legacyRequest.shouldIncludeRelatedFiles());
        assertTrue(hybridRequest.shouldIncludeRelatedFiles());
    }
}
