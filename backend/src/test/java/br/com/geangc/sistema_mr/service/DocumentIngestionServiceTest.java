package br.com.geangc.sistema_mr.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.geangc.sistema_mr.model.ChatFile;
import br.com.geangc.sistema_mr.model.DocumentStatus;
import br.com.geangc.sistema_mr.privacy.DocumentPrivacyService;
import br.com.geangc.sistema_mr.repository.DocumentRepository;
import br.com.geangc.sistema_mr.storage.DocumentStorage;
import java.time.Instant;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentIngestionServiceTest {

    @Test
    void movesVisualExtractionFailureToNeedsReviewWithoutEmbedding() throws Exception {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentStorage storage = mock(DocumentStorage.class);
        DocumentExtractor extractor = mock(DocumentExtractor.class);
        DocumentEmbeddingService embeddings = mock(DocumentEmbeddingService.class);
        DocumentPrivacyService privacy = mock(DocumentPrivacyService.class);
        UUID id = UUID.randomUUID();
        ChatFile file = new ChatFile(
                id, "chat-user", "user", "scan.png", "image/png", 10, "sha",
                id + "/original", null, DocumentStatus.QUEUED, null, 0,
                "multilingual-e5-small", Instant.now(), Instant.now()
        );
        when(repository.findById(id)).thenReturn(Optional.of(file));
        when(storage.path(file.originalStorageKey())).thenReturn(Path.of("scan.png"));
        when(extractor.extract(Path.of("scan.png"), file.originalName(), file.mimeType()))
                .thenThrow(new DocumentNeedsReviewException("OCR local insuficiente"));

        new DocumentIngestionService(repository, storage, extractor, embeddings, privacy).process(id);

        verify(repository).updateStatus(id, DocumentStatus.EXTRACTING, null);
        verify(repository).updateStatus(id, DocumentStatus.NEEDS_REVIEW, "OCR local insuficiente");
        org.mockito.Mockito.verifyNoInteractions(embeddings, privacy);
    }
}
