package br.com.geangc.sistema_mr.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiControllerTest {

    @Test
    void conversationIdIsDerivedFromAuthenticatedSubject() {
        String firstUser = AiController.conversationIdFor("google-subject-a");
        String secondUser = AiController.conversationIdFor("google-subject-b");

        assertEquals("chat-google-subject-a", firstUser);
        assertNotEquals(firstUser, secondUser);
    }

    @Test
    void conversationIdRejectsMissingSubject() {
        assertThrows(IllegalArgumentException.class, () -> AiController.conversationIdFor(" "));
    }

    @Test
    void relatedFileSearchIsOptInAndBackwardCompatible() {
        var legacyRequest = new AiController.ChatRequestDTO("Pergunta", null, null);
        var hybridRequest = new AiController.ChatRequestDTO("Pergunta", null, true);

        assertFalse(legacyRequest.shouldIncludeRelatedFiles());
        assertTrue(hybridRequest.shouldIncludeRelatedFiles());
    }
}
