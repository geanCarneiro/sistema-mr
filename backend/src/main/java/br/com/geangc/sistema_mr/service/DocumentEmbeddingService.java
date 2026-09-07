package br.com.geangc.sistema_mr.service;

import br.com.geangc.sistema_mr.configuration.DocumentProperties;
import br.com.geangc.sistema_mr.model.DocumentChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

@Service
public class DocumentEmbeddingService {

    private static final String DOCUMENT_PREFIX = "passage: ";
    private static final String QUERY_PREFIX = "query: ";

    private final DocumentEmbeddingProvider embeddingProvider;
    private final DocumentProperties properties;
    private final TokenTextSplitter splitter;

    public DocumentEmbeddingService(DocumentEmbeddingProvider embeddingProvider, DocumentProperties properties) {
        this.embeddingProvider = embeddingProvider;
        this.properties = properties;
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(properties.chunkSize())
                .withMaxNumChunks(properties.maxChunks())
                .build();
    }

    public List<DocumentChunk> embedChunks(String context) {
        List<Document> splitDocuments = splitter.apply(List.of(new Document(context)));
        List<DocumentChunk> chunks = new ArrayList<>(splitDocuments.size());
        int batchSize = Math.max(1, properties.embeddingBatchSize());

        for (int start = 0; start < splitDocuments.size(); start += batchSize) {
            int end = Math.min(start + batchSize, splitDocuments.size());
            List<String> inputs = splitDocuments.subList(start, end).stream()
                    .map(Document::getText)
                    .map(text -> DOCUMENT_PREFIX + text)
                    .toList();
            List<float[]> results = embeddingProvider.embed(inputs);
            for (int index = 0; index < results.size(); index++) {
                String chunkText = splitDocuments.get(start + index).getText();
                chunks.add(new DocumentChunk(
                        UUID.randomUUID(),
                        start + index,
                        chunkText,
                        toList(results.get(index))
                ));
            }
        }
        return chunks;
    }

    public List<Float> embedQuery(String query) {
        return toList(embeddingProvider.embed(List.of(QUERY_PREFIX + query)).getFirst());
    }

    public int estimateTokens(String text) {
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    private static List<Float> toList(float[] values) {
        List<Float> result = new ArrayList<>(values.length);
        for (float value : values) {
            result.add(value);
        }
        return result;
    }
}
