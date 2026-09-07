package br.com.geangc.sistema_mr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.geangc.sistema_mr.configuration.DocumentProperties;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentEmbeddingServiceTest {

    @Test
    void embedsAnonymizedChunksUsingDocumentAndQueryPrefixes() {
        DocumentEmbeddingProvider provider = mock(DocumentEmbeddingProvider.class);
        float[] vector = new float[384];
        vector[0] = 1.0f;
        when(provider.embed(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation -> {
                    List<String> texts = invocation.getArgument(0);
                    return texts.stream().map(ignored -> vector).toList();
                });
        DocumentEmbeddingService service = new DocumentEmbeddingService(provider, properties());

        var chunks = service.embedChunks("Nome: <NAMED_PERSON_001>\nTotal: R$ 100,00");
        var query = service.embedQuery("qual foi o total?");

        assertFalse(chunks.isEmpty());
        assertEquals(384, chunks.getFirst().embedding().size());
        assertEquals(384, query.size());
        org.mockito.Mockito.verify(provider, org.mockito.Mockito.times(2))
                .embed(org.mockito.ArgumentMatchers.argThat(texts -> texts.stream().allMatch(text ->
                        text.startsWith("passage: ") || text.startsWith("query: "))));
    }

    private static DocumentProperties properties() {
        return new DocumentProperties(
                Path.of("data/files"), 10, 20 * 1024 * 1024, 800, 1000, 20,
                "multilingual-e5-small", 384, 100, 3, .6, 200_000,
                new DocumentProperties.Ocr("http://127.0.0.1:8082", 120, 12, .55)
        );
    }
}
