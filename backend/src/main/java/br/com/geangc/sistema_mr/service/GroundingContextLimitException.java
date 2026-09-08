package br.com.geangc.sistema_mr.service;

public class GroundingContextLimitException extends RuntimeException {
    public static final String USER_MESSAGE =
            "Não consegui reunir as evidências necessárias dentro do limite desta mensagem. "
                    + "Tente fazer uma pergunta mais específica ou selecione menos arquivos.";

    public GroundingContextLimitException(String message) {
        super(message);
    }
}
