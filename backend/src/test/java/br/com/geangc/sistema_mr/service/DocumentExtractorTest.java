package br.com.geangc.sistema_mr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.geangc.sistema_mr.configuration.DocumentProperties;
import br.com.geangc.sistema_mr.configuration.DocumentVisionResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentExtractorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void usesLocalOcrWhenResultIsSufficient() throws Exception {
        PaddleOcrClient paddle = mock(PaddleOcrClient.class);
        DocumentVisionExtractor vision = mock(DocumentVisionExtractor.class);
        Path image = Files.write(temporaryDirectory.resolve("poster.png"), new byte[]{1, 2, 3});
        when(paddle.extract(image, "poster.png", "image/png")).thenReturn(result(
                List.of(line("LÉO LINS ENTERRADO VIVO", .97)), .97
        ));

        var extraction = new DocumentExtractor(properties(), paddle, vision)
                .extract(image, "poster.png", "image/png");

        assertTrue(extraction.contextMarkdown().contains("LÉO LINS ENTERRADO VIVO"));
        assertTrue(extraction.method().contains("PaddleOCR"));
        verifyNoInteractions(vision);
    }

    @Test
    void doesNotUseGeminiWhenLocalOcrInfrastructureIsUnavailable() throws Exception {
        PaddleOcrClient paddle = mock(PaddleOcrClient.class);
        DocumentVisionExtractor vision = mock(DocumentVisionExtractor.class);
        Path image = Files.write(temporaryDirectory.resolve("poster.png"), new byte[]{1});
        when(paddle.extract(image, "poster.png", "image/png"))
                .thenThrow(new OcrInfrastructureException("PaddleOCR indisponível"));

        assertThrows(
                OcrInfrastructureException.class,
                () -> new DocumentExtractor(properties(), paddle, vision)
                        .extract(image, "poster.png", "image/png")
        );
        verifyNoInteractions(vision);
    }

    @Test
    void usesGeminiWhenLocalOcrResultIsInsufficient() throws Exception {
        PaddleOcrClient paddle = mock(PaddleOcrClient.class);
        DocumentVisionExtractor vision = mock(DocumentVisionExtractor.class);
        Path image = Files.write(temporaryDirectory.resolve("poster.png"), new byte[]{1});
        when(paddle.extract(image, "poster.png", "image/png"))
                .thenReturn(result(List.of(line("LÉO", .99)), .99));
        when(vision.extract(eq(image), eq("poster.png"), eq("image/png"), contains("insuficiente")))
                .thenReturn(visionResult("LÉO LINS", "Cartaz de uma peça de humor"));

        var extraction = new DocumentExtractor(properties(), paddle, vision)
                .extract(image, "poster.png", "image/png");

        assertTrue(extraction.contextMarkdown().contains("Cartaz de uma peça de humor"));
        assertTrue(extraction.method().contains("Gemini multimodal"));
        assertTrue(extraction.warning().contains("insuficiente"));
    }

    @Test
    void usesGeminiWhenLocalOcrInferenceFails() throws Exception {
        PaddleOcrClient paddle = mock(PaddleOcrClient.class);
        DocumentVisionExtractor vision = mock(DocumentVisionExtractor.class);
        Path image = Files.write(temporaryDirectory.resolve("poster.png"), new byte[]{1});
        when(paddle.extract(image, "poster.png", "image/png"))
                .thenThrow(new OcrProcessingException("timeout"));
        when(vision.extract(eq(image), eq("poster.png"), eq("image/png"), contains("timeout")))
                .thenReturn(visionResult("LÉO LINS", "Cartaz de uma peça de humor"));

        var extraction = new DocumentExtractor(properties(), paddle, vision)
                .extract(image, "poster.png", "image/png");

        assertTrue(extraction.method().contains("Gemini multimodal"));
        verify(vision).extract(eq(image), eq("poster.png"), eq("image/png"), contains("timeout"));
    }

    @Test
    void keepsTikaForDocumentsWithNativeText() throws Exception {
        PaddleOcrClient paddle = mock(PaddleOcrClient.class);
        DocumentVisionExtractor vision = mock(DocumentVisionExtractor.class);
        Path textFile = Files.writeString(temporaryDirectory.resolve("notas.txt"), "conteúdo textual nativo");

        var extraction = new DocumentExtractor(properties(), paddle, vision)
                .extract(textFile, "notas.txt", "text/plain");

        assertEquals("Apache Tika", extraction.method());
        assertTrue(extraction.contextMarkdown().contains("conteúdo textual nativo"));
        verifyNoInteractions(paddle, vision);
    }

    private static PaddleOcrClient.OcrResult result(List<PaddleOcrClient.OcrLine> lines, double confidence) {
        return new PaddleOcrClient.OcrResult("PP-OCRv6_medium", lines, confidence, 100);
    }

    private static PaddleOcrClient.OcrLine line(String text, double confidence) {
        return new PaddleOcrClient.OcrLine(0, text, confidence, List.of());
    }

    private static DocumentVisionExtractor.VisionExtraction visionResult(String text, String description) {
        return new DocumentVisionExtractor.VisionExtraction(
                new DocumentVisionResponse(text, description, List.of(), List.of("pt")),
                "gemini-3.1-flash-lite",
                "OCR insuficiente"
        );
    }

    private static DocumentProperties properties() {
        return new DocumentProperties(
                Path.of("data/files"), 10, 20 * 1024 * 1024, 800, 1000, 20,
                "gemini-embedding-2", 768, 100, 3, .6, 200_000,
                new DocumentProperties.Ocr("http://127.0.0.1:8082", 120, 12, .55)
        );
    }
}
