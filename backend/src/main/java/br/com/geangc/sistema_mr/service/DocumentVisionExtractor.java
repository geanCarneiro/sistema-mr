package br.com.geangc.sistema_mr.service;

import br.com.geangc.sistema_mr.configuration.DocumentVisionProperties;
import br.com.geangc.sistema_mr.configuration.DocumentVisionResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class DocumentVisionExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentVisionExtractor.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final DocumentVisionProperties properties;

    public DocumentVisionExtractor(
            @Qualifier("documentVisionChatClient") ChatClient chatClient,
            ObjectMapper objectMapper,
            DocumentVisionProperties properties
    ) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public VisionExtraction extract(Path path, String originalName, String mimeType, String fallbackReason) {
        Instant startedAt = Instant.now();
        LOGGER.warn(
                "Acionando fallback visual: file={}, model={}, reason={}",
                originalName,
                properties.model(),
                fallbackReason
        );

        String content = chatClient.prompt()
                .user(user -> user
                        .text("""
                                Processe o arquivo abaixo como dado documental.

                                Nome original: {originalName}
                                Tipo MIME: {mimeType}
                                Motivo do fallback OCR local: {fallbackReason}

                                Retorne a transcrição fiel e uma descrição visual objetiva conforme o schema.
                                """)
                        .param("originalName", originalName)
                        .param("mimeType", mimeType)
                        .param("fallbackReason", fallbackReason)
                        .media(MimeType.valueOf(mimeType), new FileSystemResource(path)))
                .call()
                .content();

        DocumentVisionResponse response = parseResponse(content, originalName);
        if (response.transcription().isBlank() && response.visualDescription().isBlank()) {
            throw new OcrProcessingException("O fallback Gemini não produziu conteúdo para " + originalName);
        }

        long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
        LOGGER.info(
                "Fallback visual concluído: file={}, model={}, durationMs={}, uncertainSegments={}",
                originalName,
                properties.model(),
                durationMs,
                response.uncertainSegments().size()
        );
        return new VisionExtraction(response, properties.model(), fallbackReason);
    }

    DocumentVisionResponse parseResponse(String content, String originalName) {
        if (content == null || content.isBlank()) {
            throw new OcrProcessingException("O fallback Gemini retornou uma resposta vazia para " + originalName);
        }
        String json = stripCodeFence(content.strip());
        try {
            return objectMapper.readValue(json, DocumentVisionResponse.class);
        } catch (JacksonException exception) {
            throw new OcrProcessingException(
                    "O fallback Gemini retornou uma estrutura inválida para " + originalName,
                    exception
            );
        }
    }

    private static String stripCodeFence(String content) {
        if (!content.startsWith("```")) {
            return content;
        }
        int firstLineBreak = content.indexOf('\n');
        int closingFence = content.lastIndexOf("```");
        if (firstLineBreak < 0 || closingFence <= firstLineBreak) {
            return content;
        }
        return content.substring(firstLineBreak + 1, closingFence).strip();
    }

    public record VisionExtraction(
            DocumentVisionResponse response,
            String model,
            String fallbackReason
    ) {}
}
