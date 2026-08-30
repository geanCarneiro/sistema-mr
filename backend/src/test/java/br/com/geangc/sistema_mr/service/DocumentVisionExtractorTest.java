package br.com.geangc.sistema_mr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import br.com.geangc.sistema_mr.configuration.DocumentVisionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import tools.jackson.databind.ObjectMapper;

class DocumentVisionExtractorTest {

    @Test
    void parsesStructuredVisionResponse() {
        var extractor = extractor();

        var response = extractor.parseResponse("""
                {
                  "transcription": "LÉO LINS",
                  "visualDescription": "Cartaz de uma peça",
                  "uncertainSegments": [],
                  "detectedLanguages": ["pt"]
                }
                """, "poster.png");

        assertEquals("LÉO LINS", response.transcription());
        assertEquals("pt", response.detectedLanguages().getFirst());
    }

    @Test
    void rejectsInvalidStructuredVisionResponse() {
        assertThrows(
                OcrProcessingException.class,
                () -> extractor().parseResponse("não é JSON", "poster.png")
        );
    }

    private static DocumentVisionExtractor extractor() {
        return new DocumentVisionExtractor(
                mock(ChatClient.class),
                new ObjectMapper(),
                new DocumentVisionProperties("gemini-3.1-flash-lite", 0, "LOW", 8192)
        );
    }
}
