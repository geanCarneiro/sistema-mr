package br.com.geangc.sistema_mr.service;

import java.util.List;

public interface DocumentEmbeddingProvider {

    List<float[]> embed(List<String> texts);

    String model();
}
