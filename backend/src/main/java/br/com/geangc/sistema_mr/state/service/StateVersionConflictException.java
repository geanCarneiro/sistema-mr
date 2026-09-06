package br.com.geangc.sistema_mr.state.service;

public class StateVersionConflictException extends RuntimeException {
    public StateVersionConflictException(int expectedVersion, int currentVersion) {
        super("Conflito de versão do estado: esperada " + expectedVersion + ", atual " + currentVersion);
    }
}
