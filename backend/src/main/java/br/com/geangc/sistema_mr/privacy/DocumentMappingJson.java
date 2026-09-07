package br.com.geangc.sistema_mr.privacy;

import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class DocumentMappingJson {

    private final ObjectMapper objectMapper;

    public DocumentMappingJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(String fileId, AnonymizationResult result) {
        try {
            return objectMapper.writeValueAsString(new MappingDocument(
                    1, fileId, result.classifier(), result.spans()
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Não foi possível serializar o mapping documental", exception);
        }
    }

    public MappingDocument read(String mapping) {
        try {
            return objectMapper.readValue(mapping, MappingDocument.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Mapping documental inválido", exception);
        }
    }

    public record MappingDocument(
            int version,
            String fileId,
            String classifier,
            List<DocumentSpan> spans
    ) {}
}
