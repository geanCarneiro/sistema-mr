package br.com.geangc.sistema_mr.service;

public class DocumentNeedsReviewException extends RuntimeException {

    public DocumentNeedsReviewException(String message) {
        super(message);
    }

    public DocumentNeedsReviewException(String message, Throwable cause) {
        super(message, cause);
    }
}
