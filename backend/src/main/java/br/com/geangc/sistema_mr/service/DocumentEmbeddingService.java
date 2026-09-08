package br.com.geangc.sistema_mr.service;

import br.com.geangc.sistema_mr.configuration.DocumentProperties;
import br.com.geangc.sistema_mr.model.DocumentChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

@Service
public class DocumentEmbeddingService {

    private static final String DOCUMENT_PREFIX = "passage: ";
    private static final String QUERY_PREFIX = "query: ";
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("(?m)^(#{1,6}\\s+.+?)\\s*$", Pattern.MULTILINE);

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
        List<Document> splitDocuments = splitStructuredDocument(context);
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

    private List<Document> splitStructuredDocument(String context) {
        List<StructuredSection> sections = structuralSections(context);
        List<Document> chunks = new ArrayList<>();
        int maxChunks = Math.max(1, properties.maxChunks());
        for (StructuredSection section : sections) {
            if (chunks.size() >= maxChunks) {
                break;
            }
            List<Document> sectionChunks = splitter.apply(List.of(new Document(section.text())));
            for (Document chunk : sectionChunks) {
                if (chunks.size() >= maxChunks) {
                    break;
                }
                String chunkText = chunk.getText();
                if (section.heading() != null && !chunkText.startsWith(section.heading())) {
                    chunkText = section.heading() + "\n\n" + chunkText;
                }
                chunks.add(new Document(chunkText));
            }
        }
        return chunks;
    }

    private static List<StructuredSection> structuralSections(String context) {
        if (context == null || context.isBlank()) {
            return List.of();
        }
        Matcher matcher = MARKDOWN_HEADING.matcher(context);
        List<HeadingBoundary> headings = new ArrayList<>();
        while (matcher.find()) {
            headings.add(new HeadingBoundary(matcher.start(), matcher.end(), matcher.group(1).strip()));
        }
        if (headings.isEmpty()) {
            return List.of(new StructuredSection(null, context));
        }

        List<StructuredSection> sections = new ArrayList<>();
        HeadingBoundary first = headings.getFirst();
        if (first.start() > 0 && !context.substring(0, first.start()).isBlank()) {
            sections.add(new StructuredSection(null, context.substring(0, first.start()).strip()));
        }
        for (int index = 0; index < headings.size(); index++) {
            HeadingBoundary heading = headings.get(index);
            int end = index + 1 < headings.size() ? headings.get(index + 1).start() : context.length();
            String body = context.substring(heading.end(), end).strip();
            String sectionText = body.isBlank() ? heading.heading() : heading.heading() + "\n\n" + body;
            sections.add(new StructuredSection(heading.heading(), sectionText));
        }
        return sections;
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

    private record HeadingBoundary(int start, int end, String heading) {}

    private record StructuredSection(String heading, String text) {}
}
