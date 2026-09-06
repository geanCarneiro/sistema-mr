package br.com.geangc.sistema_mr.service;

import br.com.geangc.sistema_mr.agent.model.Subject;
import br.com.geangc.sistema_mr.agent.repository.AgentRunRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ConversationScopeService {

    private static final String GENERAL_SUBJECT_TITLE = "Chat geral";
    private final AgentRunRepository agentRunRepository;

    public ConversationScopeService(AgentRunRepository agentRunRepository) {
        this.agentRunRepository = agentRunRepository;
    }

    public ConversationScope resolve(String ownerSubject) {
        return resolve(ownerSubject, null);
    }

    public ConversationScope resolve(String ownerSubject, UUID requestedSubjectId) {
        if (ownerSubject == null || ownerSubject.isBlank()) {
            throw new IllegalArgumentException("JWT sem subject");
        }

        UUID generalSubjectId = generalSubjectId(ownerSubject);
        if (requestedSubjectId == null || requestedSubjectId.equals(generalSubjectId)) {
            return new ConversationScope(
                    universalConversationId(ownerSubject),
                    generalSubjectId,
                    GENERAL_SUBJECT_TITLE
            );
        }

        Subject subject = agentRunRepository.findOwnedSubject(requestedSubjectId, ownerSubject)
                .orElseThrow(() -> new SubjectNotFoundException("Assunto não encontrado"));
        return new ConversationScope(
                universalConversationId(ownerSubject),
                subject.id(),
                subject.title()
        );
    }

    public List<Subject> listSubjects(String ownerSubject) {
        if (ownerSubject == null || ownerSubject.isBlank()) {
            throw new IllegalArgumentException("JWT sem subject");
        }
        UUID generalSubjectId = generalSubjectId(ownerSubject);
        List<Subject> subjects = agentRunRepository.findOwnedSubjects(ownerSubject);
        if (subjects.stream().noneMatch(subject -> subject.id().equals(generalSubjectId))) {
            return java.util.stream.Stream.concat(
                    java.util.stream.Stream.of(new Subject(
                            generalSubjectId,
                            ownerSubject,
                            "GENERAL_CHAT",
                            GENERAL_SUBJECT_TITLE,
                            null
                    )),
                    subjects.stream()
            ).toList();
        }
        return subjects;
    }

    private static String universalConversationId(String ownerSubject) {
        return "chat-" + ownerSubject;
    }

    private static UUID generalSubjectId(String ownerSubject) {
        return UUID.nameUUIDFromBytes(("GENERAL_CHAT:" + ownerSubject).getBytes(StandardCharsets.UTF_8));
    }

    public record ConversationScope(String conversationId, UUID subjectId, String subjectTitle) {}
}
