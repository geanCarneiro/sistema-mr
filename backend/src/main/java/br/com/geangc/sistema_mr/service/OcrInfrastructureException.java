package br.com.geangc.sistema_mr.service;

public class OcrInfrastructureException extends IllegalStateException {

    public OcrInfrastructureException(String message) {
        super(message);
    }

    public OcrInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
