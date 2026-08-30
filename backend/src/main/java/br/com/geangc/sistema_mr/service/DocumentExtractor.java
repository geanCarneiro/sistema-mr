package br.com.geangc.sistema_mr.service;

import br.com.geangc.sistema_mr.configuration.DocumentProperties;
import br.com.geangc.sistema_mr.configuration.DocumentVisionResponse;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

@Component
public class DocumentExtractor {

    private static final String PDF_MIME_TYPE = "application/pdf";

    private final DocumentProperties properties;
    private final PaddleOcrClient paddleOcrClient;
    private final DocumentVisionExtractor visionExtractor;

    public DocumentExtractor(
            DocumentProperties properties,
            PaddleOcrClient paddleOcrClient,
            DocumentVisionExtractor visionExtractor
    ) {
        this.properties = properties;
        this.paddleOcrClient = paddleOcrClient;
        this.visionExtractor = visionExtractor;
    }

    public ExtractionResult extract(Path path, String originalName, String mimeType) throws Exception {
        if (mimeType.startsWith("image/")) {
            return extractVisual(path, originalName, mimeType);
        }

        TikaExtraction tika = extractWithTika(path, originalName);
        if (!tika.text().isBlank()) {
            return textResult(originalName, mimeType, "Apache Tika", tika.text(), null);
        }
        if (PDF_MIME_TYPE.equals(mimeType)) {
            return extractVisual(path, originalName, mimeType);
        }
        throw new IllegalArgumentException("Nenhum texto legível foi encontrado em " + originalName);
    }

    private ExtractionResult extractVisual(Path path, String originalName, String mimeType) throws Exception {
        PaddleOcrClient.OcrResult ocr;
        try {
            ocr = paddleOcrClient.extract(path, originalName, mimeType);
        } catch (OcrInfrastructureException exception) {
            throw exception;
        } catch (OcrProcessingException exception) {
            return extractWithVisionFallback(
                    path,
                    originalName,
                    mimeType,
                    "Falha operacional do PaddleOCR: " + safeReason(exception)
            );
        }

        String text = ocrText(ocr.lines());
        String insufficiency = insufficiencyReason(ocr, text);
        if (insufficiency != null) {
            return extractWithVisionFallback(path, originalName, mimeType, insufficiency);
        }
        String model = ocr.model() == null || ocr.model().isBlank() ? "modelo local" : ocr.model();
        return textResult(
                originalName,
                mimeType,
                "PaddleOCR (" + model + ")",
                text,
                null
        );
    }

    private ExtractionResult extractWithVisionFallback(
            Path path,
            String originalName,
            String mimeType,
            String reason
    ) {
        var extraction = visionExtractor.extract(path, originalName, mimeType, reason);
        DocumentVisionResponse response = extraction.response();
        String transcription = response.transcription().isBlank()
                ? "_Nenhum texto legível identificado._"
                : response.transcription();
        String visualDescription = response.visualDescription().isBlank()
                ? "_Nenhum elemento visual adicional relevante._"
                : response.visualDescription();
        String uncertainties = response.uncertainSegments().isEmpty()
                ? "_Nenhuma incerteza registrada._"
                : response.uncertainSegments().stream().map(item -> "- " + item).collect(Collectors.joining("\n"));
        String languages = response.detectedLanguages().isEmpty()
                ? "não identificado"
                : String.join(", ", response.detectedLanguages());

        String method = "Gemini multimodal (fallback do PaddleOCR; " + extraction.model() + ")";
        String markdown = """
                # Arquivo: %s

                - Tipo: `%s`
                - Extração: %s
                - Idiomas identificados: %s

                ## Transcrição

                %s

                ## Descrição visual

                %s

                ## Incertezas da extração

                %s
                """.formatted(
                originalName,
                mimeType,
                method,
                languages,
                transcription,
                visualDescription,
                uncertainties
        );
        return new ExtractionResult(markdown.strip(), method, reason);
    }

    private TikaExtraction extractWithTika(Path path, String originalName) throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, originalName);

        PDFParserConfig pdf = new PDFParserConfig();
        pdf.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, pdf);
        BodyContentHandler handler = new BodyContentHandler(-1);

        try (InputStream input = Files.newInputStream(path)) {
            new AutoDetectParser().parse(input, handler, metadata, context);
        } catch (TikaException | SAXException exception) {
            throw new IllegalArgumentException("Não foi possível extrair o conteúdo de " + originalName, exception);
        }
        return new TikaExtraction(normalize(handler.toString()));
    }

    private String insufficiencyReason(PaddleOcrClient.OcrResult ocr, String text) {
        long alphanumericCharacters = text.codePoints()
                .filter(Character::isLetterOrDigit)
                .count();
        if (alphanumericCharacters < properties.ocr().minimumTextCharacters()) {
            return "OCR local insuficiente: apenas " + alphanumericCharacters
                    + " caracteres alfanuméricos foram reconhecidos";
        }
        if (ocr.meanConfidence() < properties.ocr().minimumMeanConfidence()) {
            return "OCR local insuficiente: confiança média "
                    + "%.3f abaixo do mínimo %.3f".formatted(
                            ocr.meanConfidence(),
                            properties.ocr().minimumMeanConfidence()
                    );
        }
        return null;
    }

    private static String ocrText(List<PaddleOcrClient.OcrLine> lines) {
        Map<Integer, List<String>> pages = new TreeMap<>();
        for (PaddleOcrClient.OcrLine line : lines) {
            if (line.text() == null || line.text().isBlank()) {
                continue;
            }
            pages.computeIfAbsent(line.page(), ignored -> new ArrayList<>()).add(line.text().strip());
        }
        if (pages.isEmpty()) {
            return "";
        }
        if (pages.size() == 1 && pages.containsKey(0)) {
            return String.join("\n", pages.get(0));
        }
        return pages.entrySet().stream()
                .map(entry -> "## Página " + (entry.getKey() + 1) + "\n\n" + String.join("\n", entry.getValue()))
                .collect(Collectors.joining("\n\n"));
    }

    private static ExtractionResult textResult(
            String originalName,
            String mimeType,
            String method,
            String text,
            String warning
    ) {
        String markdown = """
                # Arquivo: %s

                - Tipo: `%s`
                - Extração: %s

                ## Conteúdo textual

                %s
                """.formatted(originalName, mimeType, method, text);
        return new ExtractionResult(markdown.strip(), method, warning);
    }

    private static String normalize(String text) {
        return text.lines()
                .map(String::stripTrailing)
                .collect(Collectors.joining("\n"))
                .replaceAll("\n{4,}", "\n\n\n")
                .strip();
    }

    private static String safeReason(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private record TikaExtraction(String text) {}

    public record ExtractionResult(String contextMarkdown, String method, String warning) {}
}
