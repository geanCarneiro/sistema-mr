package br.com.geangc.sistema_mr.privacy;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DocumentPrivacyService {

    private final DocumentAnonymizer anonymizer;
    private final DocumentMappingJson mappingJson;
    private final DocumentMappingCrypto mappingCrypto;

    public DocumentPrivacyService(
            DocumentAnonymizer anonymizer,
            DocumentMappingJson mappingJson,
            DocumentMappingCrypto mappingCrypto
    ) {
        this.anonymizer = anonymizer;
        this.mappingJson = mappingJson;
        this.mappingCrypto = mappingCrypto;
    }

    public PreparedDocument prepare(UUID fileId, String fullText) {
        AnonymizationResult result = anonymizer.anonymize(fullText);
        String mapping = mappingJson.write(fileId.toString(), result);
        byte[] encryptedMapping = mappingCrypto.encrypt(mapping, fileId.toString());
        return new PreparedDocument(result.anonymizedText(), encryptedMapping, result);
    }

    public String resolve(UUID fileId, String text, byte[] encryptedMapping) {
        String mapping = mappingCrypto.decrypt(encryptedMapping, fileId.toString());
        DocumentMappingJson.MappingDocument document = mappingJson.read(mapping);
        String resolved = text == null ? "" : text;
        for (DocumentSpan span : document.spans()) {
            resolved = resolved.replace(span.token(), span.value());
        }
        return resolved;
    }

    public record PreparedDocument(
            String anonymizedText,
            byte[] encryptedMapping,
            AnonymizationResult result
    ) {}
}
