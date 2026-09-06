package br.com.geangc.sistema_mr.agent.state;

public class StateIdempotencyConflictException extends RuntimeException {
    public StateIdempotencyConflictException(String idempotencyKey) {
        super("A chave de idempotência já foi usada para uma alteração diferente: " + idempotencyKey);
    }
}
