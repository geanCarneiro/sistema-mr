package br.com.geangc.sistema_mr.service;

import br.com.geangc.sistema_mr.model.DocumentStatus;
import br.com.geangc.sistema_mr.repository.DocumentRepository;
import br.com.geangc.sistema_mr.storage.DocumentStorage;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class DocumentIngestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentIngestionService.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final DocumentRepository repository;
    private final DocumentStorage storage;
    private final DocumentExtractor extractor;
    private final DocumentEmbeddingService embeddingService;

    public DocumentIngestionService(
            DocumentRepository repository,
            DocumentStorage storage,
            DocumentExtractor extractor,
            DocumentEmbeddingService embeddingService
    ) {
        this.repository = repository;
        this.storage = storage;
        this.extractor = extractor;
        this.embeddingService = embeddingService;
    }

    @Async("documentTaskExecutor")
    public void process(UUID id) {
        var file = repository.findById(id).orElse(null);
        if (file == null) {
            return;
        }
        try {
            repository.updateStatus(id, DocumentStatus.EXTRACTING, null);
            var extraction = extractor.extract(
                    storage.path(file.originalStorageKey()),
                    file.originalName(),
                    file.mimeType()
            );
            String contextKey = storage.writeContext(id, extraction.contextMarkdown());

            repository.updateStatus(id, DocumentStatus.EMBEDDING, null);
            var chunks = embeddingService.embedChunks(extraction.contextMarkdown());
            if (chunks.isEmpty()) {
                throw new IllegalStateException("O documento não gerou chunks para indexação");
            }
            repository.markReady(
                    id,
                    contextKey,
                    extraction.method(),
                    extraction.warning(),
                    embeddingService.estimateTokens(extraction.contextMarkdown()),
                    chunks
            );
        } catch (Exception exception) {
            LOGGER.error("Falha ao processar o arquivo {}", id, exception);
            repository.updateStatus(id, DocumentStatus.FAILED, safeMessage(exception));
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = "Falha ao extrair ou indexar o arquivo";
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
