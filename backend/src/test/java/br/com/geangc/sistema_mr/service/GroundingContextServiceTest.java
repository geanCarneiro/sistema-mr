package br.com.geangc.sistema_mr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.geangc.sistema_mr.configuration.DocumentProperties;
import br.com.geangc.sistema_mr.model.ChatFile;
import br.com.geangc.sistema_mr.model.DocumentStatus;
import br.com.geangc.sistema_mr.repository.DocumentRepository;
import br.com.geangc.sistema_mr.storage.DocumentStorage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroundingContextServiceTest {

    @Test
    void includesFullTextForAnExplicitAttachment() throws Exception {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentStorage storage = mock(DocumentStorage.class);
        DocumentEmbeddingService embeddings = mock(DocumentEmbeddingService.class);
        UUID id = UUID.randomUUID();
        ChatFile file = readyFile(id, 20);

        when(repository.findReadyOwnedByIds(List.of(id), "chat-owner", "owner")).thenReturn(List.of(file));
        when(repository.hasReadyFiles("chat-owner", "owner")).thenReturn(false);
        when(storage.readText("context-key")).thenReturn("CONTEÚDO COMPLETO DO ARQUIVO");

        var service = new GroundingContextService(repository, storage, embeddings, properties(200_000));
        var prepared = service.prepare("chat-owner", "owner", "Resuma", List.of(id));

        assertTrue(prepared.modelPrompt().contains("CONTEÚDO COMPLETO DO ARQUIVO"));
        assertTrue(prepared.modelPrompt().contains("Resuma"));
        assertEquals(1, prepared.files().size());
        assertTrue(prepared.files().getFirst().explicitlyAttached());
    }

    @Test
    void rejectsExplicitAttachmentsThatExceedTheContextBudget() {
        DocumentRepository repository = mock(DocumentRepository.class);
        UUID id = UUID.randomUUID();
        when(repository.findReadyOwnedByIds(List.of(id), "chat-owner", "owner"))
                .thenReturn(List.of(readyFile(id, 101)));
        when(repository.hasReadyFiles("chat-owner", "owner")).thenReturn(false);

        var service = new GroundingContextService(
                repository, mock(DocumentStorage.class), mock(DocumentEmbeddingService.class), properties(100)
        );

        assertThrows(GroundingContextLimitException.class,
                () -> service.prepare("chat-owner", "owner", "Pergunta", List.of(id)));
    }

    private static ChatFile readyFile(UUID id, int tokenCount) {
        Instant now = Instant.now();
        return new ChatFile(
                id, "chat-owner", "owner", "relatório.pdf", "application/pdf", 10, "hash",
                id + "/original", "context-key", DocumentStatus.READY, null, tokenCount,
                "gemini-embedding-2", now, now
        );
    }

    private static DocumentProperties properties(int budget) {
        return new DocumentProperties(
                Path.of("data/files"), 10, 20 * 1024 * 1024, 800, 1000, 20,
                "gemini-embedding-2", 768, 100, 3, .6, budget,
                new DocumentProperties.Ocr("http://127.0.0.1:8082", 120, 12, .55)
        );
    }
}
