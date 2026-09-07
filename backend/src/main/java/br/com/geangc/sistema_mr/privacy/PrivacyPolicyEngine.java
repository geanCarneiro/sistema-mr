package br.com.geangc.sistema_mr.privacy;

import br.com.geangc.sistema_mr.model.ChatFile;
import br.com.geangc.sistema_mr.model.DocumentSensitivity;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PrivacyPolicyEngine {

    public PrivacyDecision decide(List<ChatFile> files) {
        DocumentSensitivity highest = files == null || files.isEmpty()
                ? DocumentSensitivity.NORMAL
                : files.stream()
                        .map(ChatFile::sensitivity)
                        .map(value -> value == null ? DocumentSensitivity.UNKNOWN : value)
                        .max(java.util.Comparator.comparingInt(Enum::ordinal))
                        .orElse(DocumentSensitivity.UNKNOWN);

        if (highest == DocumentSensitivity.SENSITIVE
                || highest == DocumentSensitivity.RESTRICTED
                || highest == DocumentSensitivity.UNKNOWN) {
            return new PrivacyDecision(
                    PrivacyMode.LOCAL_ONLY,
                    highest,
                    "CONTEXT_ANONYMIZED_LOCAL",
                    "O conteúdo foi classificado como sensível, restrito ou desconhecido"
            );
        }
        return new PrivacyDecision(
                PrivacyMode.CLOUD_MINIMIZED,
                highest,
                "CONTEXT_ANONYMIZED",
                "Apenas a representação minimizada e anonimizada pode seguir para o cloud"
        );
    }
}
