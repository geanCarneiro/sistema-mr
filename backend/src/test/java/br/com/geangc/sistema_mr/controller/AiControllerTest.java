package br.com.geangc.sistema_mr.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
