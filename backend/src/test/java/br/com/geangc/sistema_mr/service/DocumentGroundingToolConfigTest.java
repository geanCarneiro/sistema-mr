package br.com.geangc.sistema_mr.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

class DocumentGroundingToolConfigTest {

    @Test
    void searchesEvidenceUsingTheBackendToolScope() {
        GroundingContextService service = mock(GroundingContextService.class);
        DocumentGroundingToolConfig config = new DocumentGroundingToolConfig(service);
        UUID fileId = UUID.randomUUID();
        when(service.searchEvidence("chat-owner", "owner", "valor total", 3))
                .thenReturn(List.of(new GroundingContextService.GroundingEvidence(
                        fileId, "contrato.pdf", 4, .91, "TOTAL")));

        var result = config.searchDocumentEvidence(
                new DocumentGroundingToolConfig.SearchRequest("valor total", 3),
                context("CLOUD_MINIMIZED"));

        verify(service).searchEvidence(eq("chat-owner"), eq("owner"), eq("valor total"), eq(3));
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
    }

    @Test
    void cannotReadFullDocumentFromCloudExecution() {
        GroundingContextService service = new GroundingContextService(
                mock(br.com.geangc.sistema_mr.repository.DocumentRepository.class),
                mock(br.com.geangc.sistema_mr.storage.DocumentStorage.class),
                mock(DocumentEmbeddingService.class),
                new br.com.geangc.sistema_mr.configuration.DocumentProperties(
                        java.nio.file.Path.of("data/files"), 10, 20 * 1024 * 1024, 800, 1000, 20,
                        "multilingual-e5-small", 384, 100, 3, .6, 200_000,
                        new br.com.geangc.sistema_mr.configuration.DocumentProperties.Ocr(
                                "http://127.0.0.1:8082", 120, 12, .55)));
        DocumentGroundingToolConfig config = new DocumentGroundingToolConfig(service);

        assertThrows(IllegalArgumentException.class, () -> config.readFullDocument(
                new DocumentGroundingToolConfig.FullDocumentRequest(UUID.randomUUID().toString()),
                context("CLOUD_MINIMIZED")));
    }

    private static ToolContext context(String privacyMode) {
        return new ToolContext(Map.of(
                "ownerSubject", "owner", "conversationId", "chat-owner",
                "subjectId", "subject-1", "runId", UUID.randomUUID().toString(),
                "privacyMode", privacyMode));
    }
}
