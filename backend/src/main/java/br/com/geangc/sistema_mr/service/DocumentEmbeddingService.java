package br.com.geangc.sistema_mr.service;

import br.com.geangc.sistema_mr.configuration.DocumentProperties;
import br.com.geangc.sistema_mr.model.DocumentChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

@Service
public class DocumentEmbeddingService {

    private static final String DOCUMENT_PREFIX = "Represent this document for retrieval:\n";
    private static final String QUERY_PREFIX = "Represent this query for retrieving relevant documents:\n";

    private final EmbeddingModel embeddingModel;
    private final DocumentProperties properties;
    private final TokenTextSplitter splitter;

    public DocumentEmbeddingService(EmbeddingModel embeddingModel, DocumentProperties properties) {
        this.embeddingModel = embeddingModel;
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
            var response = embeddingModel.call(new EmbeddingRequest(inputs, null));
            var results = response.getResults();
            if (results.size() != inputs.size()) {
                throw new IllegalStateException("A API de embeddings retornou uma quantidade inesperada de vetores");
            }
            for (int index = 0; index < results.size(); index++) {
                String chunkText = splitDocuments.get(start + index).getText();
                chunks.add(new DocumentChunk(
                        UUID.randomUUID(),
                        start + index,
                        chunkText,
                        toList(results.get(index).getOutput())
                ));
            }
        }
        return chunks;
    }

    public List<Float> embedQuery(String query) {
        return toList(embeddingModel.embed(QUERY_PREFIX + query));
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
