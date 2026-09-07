package br.com.geangc.sistema_mr.privacy;

import br.com.geangc.sistema_mr.model.DocumentSensitivity;

public record PrivacyDecision(
        PrivacyMode mode,
        DocumentSensitivity highestSensitivity,
        String representation,
        String reason
) {
    public boolean localOnly() {
        return mode == PrivacyMode.LOCAL_ONLY;
    }
}
