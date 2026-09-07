package br.com.geangc.sistema_mr.privacy;

import java.util.List;

public record AnonymizationResult(
        String anonymizedText,
        List<DocumentSpan> spans,
        String classifier,
        br.com.geangc.sistema_mr.model.DocumentSensitivity highestSensitivity
) {
    public AnonymizationResult(String anonymizedText, List<DocumentSpan> spans, String classifier) {
        this(anonymizedText, spans, classifier, br.com.geangc.sistema_mr.model.DocumentSensitivity.NORMAL);
    }

    public AnonymizationResult {
        spans = List.copyOf(spans);
        highestSensitivity = highestSensitivity == null
                ? br.com.geangc.sistema_mr.model.DocumentSensitivity.NORMAL
                : highestSensitivity;
    }
}
