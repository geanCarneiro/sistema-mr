package br.com.geangc.sistema_mr.service;

import br.com.geangc.sistema_mr.configuration.DocumentProperties;
import br.com.geangc.sistema_mr.model.ChatFile;
import br.com.geangc.sistema_mr.model.DocumentStatus;
import br.com.geangc.sistema_mr.repository.DocumentRepository;
import br.com.geangc.sistema_mr.storage.DocumentStorage;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentServiceTest {

    private DocumentRepository repository;
    private DocumentStorage storage;
    private DocumentIngestionService ingestionService;
    private DocumentProperties properties;
    private DocumentService service;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        storage = mock(DocumentStorage.class);
        ingestionService = mock(DocumentIngestionService.class);
        properties = mock(DocumentProperties.class);
        service = new DocumentService(repository, storage, ingestionService, properties);
    }

    @Test
    void retrySuccessWhenStatusIsFailed() {
        UUID id = UUID.randomUUID();
        String convId = "chat-user1";
        String owner = "user1";
        Instant now = Instant.now();

        ChatFile failedFile = new ChatFile(
                id, convId, owner, "test.pdf", "application/pdf", 1024L, "sha256",
                "key/original", "key/context.md", DocumentStatus.FAILED, "Erro OCR", 0, "embedding-model", now, now
        );
        ChatFile queuedFile = new ChatFile(
                id, convId, owner, "test.pdf", "application/pdf", 1024L, "sha256",
                "key/original", null, DocumentStatus.QUEUED, null, 0, "embedding-model", now, now
        );

        when(repository.findOwned(id, convId, owner)).thenReturn(Optional.of(failedFile));
        when(repository.resetForRetry(id, convId, owner)).thenReturn(Optional.of(queuedFile));

        ChatFile result = service.retry(id, convId, owner);

        assertEquals(DocumentStatus.QUEUED, result.status());
        verify(ingestionService).process(id);
    }

    @Test
    void retryThrowsExceptionWhenStatusIsNotFailed() {
        UUID id = UUID.randomUUID();
        String convId = "chat-user1";
        String owner = "user1";
        Instant now = Instant.now();

        ChatFile readyFile = new ChatFile(
                id, convId, owner, "test.pdf", "application/pdf", 1024L, "sha256",
                "key/original", "key/context.md", DocumentStatus.READY, null, 100, "embedding-model", now, now
        );

        when(repository.findOwned(id, convId, owner)).thenReturn(Optional.of(readyFile));

        assertThrows(IllegalArgumentException.class, () -> service.retry(id, convId, owner));
        verifyNoInteractions(ingestionService);
    }

    @Test
    void retryThrowsNotFoundWhenFileDoesNotExistOrNotOwned() {
        UUID id = UUID.randomUUID();
        String convId = "chat-user1";
        String owner = "user1";

        when(repository.findOwned(id, convId, owner)).thenReturn(Optional.empty());

        assertThrows(DocumentNotFoundException.class, () -> service.retry(id, convId, owner));
        verifyNoInteractions(ingestionService);
    }
}
