package br.com.geangc.sistema_mr.service;

import br.com.geangc.sistema_mr.repository.DocumentRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DocumentProcessingRecovery implements ApplicationRunner {

    private final DocumentRepository repository;
    private final DocumentIngestionService ingestionService;

    public DocumentProcessingRecovery(DocumentRepository repository, DocumentIngestionService ingestionService) {
        this.repository = repository;
        this.ingestionService = ingestionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        repository.findPending().forEach(file -> ingestionService.process(file.id()));
    }
}
