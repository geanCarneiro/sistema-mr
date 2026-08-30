package br.com.geangc.sistema_mr.configuration;

import java.nio.file.Path;
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
        Ocr ocr
) {
    public record Ocr(
            String serviceUrl,
            int timeoutSeconds,
            int minimumTextCharacters,
            double minimumMeanConfidence
    ) {}
}
