package br.com.geangc.sistema_mr.model;

import java.util.List;
import java.util.UUID;

public record DocumentChunk(
        UUID id,
        int position,
        String text,
        List<Float> embedding
) {}
