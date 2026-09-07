package br.com.geangc.sistema_mr.service;

import br.com.geangc.sistema_mr.model.DocumentStatus;
import br.com.geangc.sistema_mr.repository.DocumentRepository;
import br.com.geangc.sistema_mr.storage.DocumentStorage;
import br.com.geangc.sistema_mr.privacy.DocumentPrivacyService;
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
    private final DocumentPrivacyService privacyService;

    public DocumentIngestionService(
            DocumentRepository repository,
            DocumentStorage storage,
            DocumentExtractor extractor,
            DocumentEmbeddingService embeddingService,
            DocumentPrivacyService privacyService
    ) {
        this.repository = repository;
        this.storage = storage;
        this.extractor = extractor;
        this.embeddingService = embeddingService;
        this.privacyService = privacyService;
    }

    @Async("documentTaskExecutor")
    public void process(UUID id) {
        var file = repository.findById(id).orElse(null);
        if (file == null) {
            return;
        }
        try {
            if (file.mappingStorageKey() == null && file.contextStorageKey() != null) {
                repository.clearChunksForReprocessing(id);
            }
            repository.updateStatus(id, DocumentStatus.EXTRACTING, null);
            var extraction = extract(file);
            var prepared = privacyService.prepare(id, extraction.contextMarkdown());
            String fullContextKey = storage.writeFullContext(id, extraction.contextMarkdown());
            String contextKey = storage.writeContext(id, prepared.anonymizedText());
            String mappingKey = storage.writeMapping(id, prepared.encryptedMapping());

            repository.updateStatus(id, DocumentStatus.EMBEDDING, null);
            var chunks = embeddingService.embedChunks(prepared.anonymizedText());
            if (chunks.isEmpty()) {
                throw new IllegalStateException("O documento não gerou chunks para indexação");
            }
            repository.markReady(
                    id,
                    contextKey,
                    fullContextKey,
                    mappingKey,
                    extraction.method(),
                    extraction.warning(),
                    embeddingService.estimateTokens(extraction.contextMarkdown()),
                    prepared.result().highestSensitivity(),
                    chunks
            );
        } catch (DocumentNeedsReviewException exception) {
            LOGGER.warn("O arquivo {} precisa de revisão humana: {}", id, exception.getMessage());
            repository.updateStatus(id, DocumentStatus.NEEDS_REVIEW, safeMessage(exception));
        } catch (Exception exception) {
            LOGGER.error("Falha ao processar o arquivo {}", id, exception);
            repository.updateStatus(id, DocumentStatus.FAILED, safeMessage(exception));
        }
    }

    private DocumentExtractor.ExtractionResult extract(br.com.geangc.sistema_mr.model.ChatFile file) throws Exception {
        if (file.mappingStorageKey() == null && file.contextStorageKey() != null) {
            return new DocumentExtractor.ExtractionResult(
                    storage.readText(file.contextStorageKey()),
                    "Migração de privacidade",
                    "Representação legada reprocessada com classificação local"
            );
        }
        return extractor.extract(
                storage.path(file.originalStorageKey()),
                file.originalName(),
                file.mimeType()
        );
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = "Falha ao extrair ou indexar o arquivo";
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
