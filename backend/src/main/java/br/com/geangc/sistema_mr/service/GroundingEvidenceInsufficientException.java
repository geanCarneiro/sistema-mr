package br.com.geangc.sistema_mr.service;

public class GroundingEvidenceInsufficientException extends RuntimeException {
    public static final String USER_MESSAGE =
            "Não encontrei evidências suficientes nos arquivos selecionados para responder com segurança. "
                    + "Tente reformular a pergunta ou selecione outro arquivo.";

    public GroundingEvidenceInsufficientException(String message) {
        super(message);
    }
}
