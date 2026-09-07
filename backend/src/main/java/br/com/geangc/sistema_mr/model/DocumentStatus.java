package br.com.geangc.sistema_mr.model;

public enum DocumentStatus {
    QUEUED,
    EXTRACTING,
    EMBEDDING,
    NEEDS_REVIEW,
    READY,
    FAILED
}
