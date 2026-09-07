package br.com.geangc.sistema_mr.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.geangc.sistema_mr.model.ChatFile;
import br.com.geangc.sistema_mr.model.DocumentSensitivity;
import br.com.geangc.sistema_mr.model.DocumentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PrivacyPolicyEngineTest {

    private final PrivacyPolicyEngine engine = new PrivacyPolicyEngine();

    @Test
    void keepsSensitiveDocumentLocalByDefault() {
        PrivacyDecision decision = engine.decide(List.of(file(DocumentSensitivity.SENSITIVE)));

        assertEquals(PrivacyMode.LOCAL_ONLY, decision.mode());
        assertEquals(DocumentSensitivity.SENSITIVE, decision.highestSensitivity());
    }

    @Test
    void allowsOnlyMinimizedRepresentationForPersonalDocument() {
        PrivacyDecision decision = engine.decide(List.of(file(DocumentSensitivity.PERSONAL)));

        assertEquals(PrivacyMode.CLOUD_MINIMIZED, decision.mode());
        assertEquals("CONTEXT_ANONYMIZED", decision.representation());
    }

    private static ChatFile file(DocumentSensitivity sensitivity) {
        Instant now = Instant.now();
        return new ChatFile(
                UUID.randomUUID(), "chat-owner", "owner", "arquivo.txt", "text/plain", 10,
                "sha", "original", "context", DocumentStatus.READY, null, 2,
                "multilingual-e5-small", now, now, sensitivity
        );
    }
}
