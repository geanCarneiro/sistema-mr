package br.com.geangc.sistema_mr.configuration;

import java.nio.file.Path;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.documents")
public record DocumentProperties(
        Path storageRoot,
        int maxFilesPerUpload,
        long maxFileSizeBytes,
        int chunkSize,
        int maxChunks,
        int embeddingBatchSize,
        String embeddingModel,
        int embeddingDimensions,
        int retrievalCandidates,
        int retrievalFileLimit,
        double similarityThreshold,
        int contextTokenBudget,
        Ocr ocr,
        String embeddingServiceUrl
) {
    @ConstructorBinding
    public DocumentProperties {
    }

    public record Ocr(
            String serviceUrl,
            int timeoutSeconds,
            int minimumTextCharacters,
            double minimumMeanConfidence
        ) {}

    public DocumentProperties(
            Path storageRoot,
            int maxFilesPerUpload,
            long maxFileSizeBytes,
            int chunkSize,
            int maxChunks,
            int embeddingBatchSize,
            String embeddingModel,
            int embeddingDimensions,
            int retrievalCandidates,
            int retrievalFileLimit,
            double similarityThreshold,
            int contextTokenBudget,
            Ocr ocr
    ) {
        this(storageRoot, maxFilesPerUpload, maxFileSizeBytes, chunkSize, maxChunks,
                embeddingBatchSize, embeddingModel, embeddingDimensions, retrievalCandidates,
                retrievalFileLimit, similarityThreshold, contextTokenBudget, ocr,
                "http://127.0.0.1:8083");
    }
}
