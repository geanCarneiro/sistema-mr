package br.com.geangc.sistema_mr.service;

import br.com.geangc.sistema_mr.configuration.DocumentProperties;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

@Component
public class DocumentExtractor {

    private final DocumentProperties properties;

    public DocumentExtractor(DocumentProperties properties) {
        this.properties = properties;
    }

    public ExtractionResult extract(Path path, String originalName, String mimeType) throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, originalName);

        ParseContext parseContext = new ParseContext();
        configureOcr(parseContext);
        BodyContentHandler handler = new BodyContentHandler(-1);

        try (InputStream input = Files.newInputStream(path)) {
            new AutoDetectParser().parse(input, handler, metadata, parseContext);
        } catch (TikaException | SAXException exception) {
            throw new IllegalArgumentException("Não foi possível extrair o conteúdo de " + originalName, exception);
        }

        String text = normalize(handler.toString());
        if (text.isBlank()) {
            throw new IllegalArgumentException("Nenhum texto legível foi encontrado em " + originalName);
        }

        String parsedBy = String.join(", ", metadata.getValues("X-TIKA:Parsed-By"));
        boolean usedOcr = parsedBy.toLowerCase(Locale.ROOT).contains("tesseract");
        String method = usedOcr ? "Apache Tika + Tesseract OCR" : "Apache Tika";
        String warning = properties.ocr().enabled() && !usedOcr && mimeType.startsWith("image/")
                ? "OCR não foi identificado nos metadados do parser"
                : null;

        String markdown = """
                # Arquivo: %s

                - Tipo: `%s`
                - Extração: %s

                ## Conteúdo textual

                %s
                """.formatted(originalName, mimeType, method, text);
        return new ExtractionResult(markdown, method, warning);
    }

    private void configureOcr(ParseContext context) {
        PDFParserConfig pdf = new PDFParserConfig();
        if (properties.ocr().enabled()) {
            TesseractOCRConfig ocr = new TesseractOCRConfig();
            ocr.setLanguage(properties.ocr().languages());
            ocr.setTimeoutSeconds(properties.ocr().timeoutSeconds());
            context.set(TesseractOCRConfig.class, ocr);
            pdf.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.AUTO);
        } else {
            pdf.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);
        }
        context.set(PDFParserConfig.class, pdf);
    }

    private static String normalize(String text) {
        return text.lines()
                .map(String::stripTrailing)
                .collect(Collectors.joining("\n"))
                .replaceAll("\n{4,}", "\n\n\n")
                .strip();
    }

    public record ExtractionResult(String contextMarkdown, String method, String warning) {}
}
