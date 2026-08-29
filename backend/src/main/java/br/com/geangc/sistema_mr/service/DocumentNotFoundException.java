package br.com.geangc.sistema_mr.service;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException() {
        super("Arquivo não encontrado");
    }
}
