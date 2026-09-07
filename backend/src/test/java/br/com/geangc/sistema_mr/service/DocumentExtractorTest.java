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
        Path image = Files.write(temporaryDirectory.resolve("poster.png"), new byte[]{1, 2, 3});
        when(paddle.extract(image, "poster.png", "image/png")).thenReturn(result(
                List.of(line("LÉO LINS ENTERRADO VIVO", .97)), .97
        ));

        var extraction = new DocumentExtractor(properties(), paddle)
                .extract(image, "poster.png", "image/png");

        assertTrue(extraction.contextMarkdown().contains("LÉO LINS ENTERRADO VIVO"));
        assertTrue(extraction.method().contains("PaddleOCR"));
    }

    @Test
    void doesNotUseGeminiWhenLocalOcrInfrastructureIsUnavailable() throws Exception {
        PaddleOcrClient paddle = mock(PaddleOcrClient.class);
        Path image = Files.write(temporaryDirectory.resolve("poster.png"), new byte[]{1});
        when(paddle.extract(image, "poster.png", "image/png"))
                .thenThrow(new OcrInfrastructureException("PaddleOCR indisponível"));

        assertThrows(
                DocumentNeedsReviewException.class,
                () -> new DocumentExtractor(properties(), paddle)
                        .extract(image, "poster.png", "image/png")
        );
    }

    @Test
    void requestsHumanReviewWhenLocalOcrResultIsInsufficient() throws Exception {
        PaddleOcrClient paddle = mock(PaddleOcrClient.class);
        Path image = Files.write(temporaryDirectory.resolve("poster.png"), new byte[]{1});
        when(paddle.extract(image, "poster.png", "image/png"))
                .thenReturn(result(List.of(line("LÉO", .99)), .99));
        var exception = assertThrows(DocumentNeedsReviewException.class, () ->
                new DocumentExtractor(properties(), paddle).extract(image, "poster.png", "image/png"));

        assertTrue(exception.getMessage().contains("insuficiente"));
    }

    @Test
    void requestsHumanReviewWhenLocalOcrInferenceFails() throws Exception {
        PaddleOcrClient paddle = mock(PaddleOcrClient.class);
        Path image = Files.write(temporaryDirectory.resolve("poster.png"), new byte[]{1});
        when(paddle.extract(image, "poster.png", "image/png"))
                .thenThrow(new OcrProcessingException("timeout"));
        var exception = assertThrows(DocumentNeedsReviewException.class, () ->
                new DocumentExtractor(properties(), paddle).extract(image, "poster.png", "image/png"));

        assertTrue(exception.getMessage().contains("timeout"));
    }

    @Test
    void keepsTikaForDocumentsWithNativeText() throws Exception {
        PaddleOcrClient paddle = mock(PaddleOcrClient.class);
        Path textFile = Files.writeString(temporaryDirectory.resolve("notas.txt"), "conteúdo textual nativo");

        var extraction = new DocumentExtractor(properties(), paddle)
                .extract(textFile, "notas.txt", "text/plain");

        assertEquals("Apache Tika", extraction.method());
        assertTrue(extraction.contextMarkdown().contains("conteúdo textual nativo"));
        verifyNoInteractions(paddle);
    }

    private static PaddleOcrClient.OcrResult result(List<PaddleOcrClient.OcrLine> lines, double confidence) {
        return new PaddleOcrClient.OcrResult("PP-OCRv6_medium", lines, confidence, 100);
    }

    private static PaddleOcrClient.OcrLine line(String text, double confidence) {
        return new PaddleOcrClient.OcrLine(0, text, confidence, List.of());
    }

    private static DocumentProperties properties() {
        return new DocumentProperties(
                Path.of("data/files"), 10, 20 * 1024 * 1024, 800, 1000, 20,
                "gemini-embedding-2", 768, 100, 3, .6, 200_000,
                new DocumentProperties.Ocr("http://127.0.0.1:8082", 120, 12, .55)
        );
    }
}
