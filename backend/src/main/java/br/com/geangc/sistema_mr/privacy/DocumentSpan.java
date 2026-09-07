package br.com.geangc.sistema_mr.privacy;

import br.com.geangc.sistema_mr.model.DocumentSensitivity;

public record DocumentSpan(
        int start,
        int end,
        String category,
        DocumentSensitivity sensitivity,
        String token,
        String value
) {}
