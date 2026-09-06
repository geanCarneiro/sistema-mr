package br.com.geangc.sistema_mr.agent.repository;

import br.com.geangc.sistema_mr.agent.model.AgentRun;
import br.com.geangc.sistema_mr.agent.model.AgentRunStatus;
import br.com.geangc.sistema_mr.agent.model.AgentRunTrigger;
import br.com.geangc.sistema_mr.agent.model.Subject;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.springframework.stereotype.Repository;

@Repository
public class AgentRunRepository {

    private final Driver driver;

    public AgentRunRepository(Driver driver) {
        this.driver = driver;
    }

    @PostConstruct
    void initializeSchema() {
        try (var session = driver.session()) {
            session.run("CREATE CONSTRAINT assunto_id IF NOT EXISTS "
                    + "FOR (n:Assunto) REQUIRE n.id IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT agent_run_id IF NOT EXISTS "
                    + "FOR (n:AgentRun) REQUIRE n.id IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT model_invocation_id IF NOT EXISTS "
                    + "FOR (n:ModelInvocation) REQUIRE n.id IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT agent_tool_call_id IF NOT EXISTS "
                    + "FOR (n:AgentToolCall) REQUIRE n.id IS UNIQUE").consume();
            session.run("CREATE INDEX agent_run_owner IF NOT EXISTS "
                    + "FOR (n:AgentRun) ON (n.ownerSubject)").consume();
            session.run("CREATE INDEX agent_run_status IF NOT EXISTS "
                    + "FOR (n:AgentRun) ON (n.status)").consume();
        }
    }

    public void create(AgentRun run, String subjectTitle) {
        String query = """
                MERGE (subject:Assunto {id: $subjectId})
                ON CREATE SET subject.ownerSubject = $ownerSubject,
                              subject.kind = 'GENERAL_CHAT',
                              subject.title = $subjectTitle,
                              subject.createdAt = $createdAt
                WITH subject
                WHERE subject.ownerSubject = $ownerSubject
                MERGE (run:AgentRun {id: $runId})
                ON CREATE SET run.subjectId = $subjectId,
                              run.conversationId = $conversationId,
                              run.ownerSubject = $ownerSubject,
                              run.trigger = $trigger,
                              run.status = $status,
                              run.version = 0,
                              run.steps = 0,
                              run.modelInvocations = 0,
                              run.toolCalls = 0,
                              run.createdAt = $createdAt
                WITH subject, run
                WHERE run.ownerSubject = $ownerSubject
                  AND run.subjectId = $subjectId
                MERGE (subject)-[:POSSUI_RUN]->(run)
                RETURN run
                """;

        Map<String, Object> parameters = Map.of(
                "subjectId", run.subjectId().toString(),
                "subjectTitle", subjectTitle,
                "ownerSubject", run.ownerSubject(),
                "createdAt", run.createdAt().toString(),
                "runId", run.id().toString(),
                "conversationId", run.conversationId(),
                "trigger", run.trigger().name(),
                "status", run.status().name()
        );

        try (var session = driver.session()) {
            session.executeWrite(transaction -> {
                var result = transaction.run(query, parameters);
                if (!result.hasNext()) {
                    throw new IllegalArgumentException("O assunto da execução não pertence ao usuário");
                }
                result.consume();
                return null;
            });
        }
    }

    public Optional<Subject> findOwnedSubject(UUID subjectId, String ownerSubject) {
        String query = """
                MATCH (subject:Assunto {id: $subjectId, ownerSubject: $ownerSubject})
                RETURN subject
                """;
        try (var session = driver.session()) {
            return session.executeRead(transaction -> transaction.run(query, Map.of(
                    "subjectId", subjectId.toString(),
                    "ownerSubject", ownerSubject
            )).stream()
                    .findFirst()
                    .map(this::mapSubject));
        }
    }

    public List<Subject> findOwnedSubjects(String ownerSubject) {
        String query = """
                MATCH (subject:Assunto {ownerSubject: $ownerSubject})
                RETURN subject
                ORDER BY subject.createdAt ASC
                """;
        try (var session = driver.session()) {
            return session.executeRead(transaction -> transaction.run(query, Map.of("ownerSubject", ownerSubject))
                    .list(this::mapSubject));
        }
    }

    public boolean claim(UUID runId, String ownerSubject) {
        String query = """
                MATCH (subject:Assunto)-[:POSSUI_RUN]->(run:AgentRun {
                    id: $runId,
                    ownerSubject: $ownerSubject
                })
                WHERE run.status IN $claimable
                  AND NOT EXISTS {
                      MATCH (subject)-[:POSSUI_RUN]->(other:AgentRun)
                      WHERE other.id <> run.id
                        AND other.status IN $blocking
                  }
                SET run.status = 'RUNNING',
                    run.startedAt = coalesce(run.startedAt, $startedAt),
                    run.version = run.version + 1
                RETURN run
                """;

        try (var session = driver.session()) {
            return session.executeWrite(transaction -> transaction.run(query, Map.of(
                    "runId", runId.toString(),
                    "ownerSubject", ownerSubject,
                    "startedAt", Instant.now().toString(),
                    "claimable", List.of(
                            AgentRunStatus.SCHEDULED.name(),
                            AgentRunStatus.WAITING_FOR_CAPACITY.name()
                    ),
                    "blocking", List.of(
                            AgentRunStatus.RUNNING.name(),
                            AgentRunStatus.WAITING_FOR_USER.name(),
                            AgentRunStatus.WAITING_FOR_TOOL.name()
                    )
            )).hasNext());
        }
    }

    public List<AgentRun> findResumable() {
        String query = """
                MATCH (run:AgentRun)
                WHERE run.status IN ['SCHEDULED', 'WAITING_FOR_CAPACITY', 'WAITING_FOR_TOOL']
                RETURN run
                ORDER BY run.createdAt ASC
                """;

        try (var session = driver.session()) {
            return session.executeRead(transaction -> transaction.run(query)
                    .list(this::mapRun));
        }
    }

    public void recordInvocation(
            UUID runId,
            String ownerSubject,
            int ordinal,
            String routeId,
            String providerId,
            String modelId,
            String selectionReason,
            boolean toolCallResponse,
            Instant startedAt,
            Instant completedAt
    ) {
        String invocationId = UUID.randomUUID().toString();
        String query = """
                MATCH (run:AgentRun {id: $runId, ownerSubject: $ownerSubject})
                CREATE (invocation:ModelInvocation {
                    id: $invocationId,
                    runId: $runId,
                    ordinal: $ordinal,
                    routeId: $routeId,
                    providerId: $providerId,
                    modelId: $modelId,
                    selectionReason: $selectionReason,
                    toolCallResponse: $toolCallResponse,
                    startedAt: $startedAt,
                    completedAt: $completedAt
                })
                SET run.modelInvocations = run.modelInvocations + 1,
                    run.steps = run.steps + 1,
                    run.version = run.version + 1
                MERGE (run)-[:POSSUI_INVOCACAO]->(invocation)
                RETURN invocation
                """;

        try (var session = driver.session()) {
            session.executeWrite(transaction -> {
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("runId", runId.toString());
                parameters.put("ownerSubject", ownerSubject);
                parameters.put("invocationId", invocationId);
                parameters.put("ordinal", ordinal);
                parameters.put("routeId", routeId);
                parameters.put("providerId", providerId);
                parameters.put("modelId", modelId);
                parameters.put("selectionReason", selectionReason);
                parameters.put("toolCallResponse", toolCallResponse);
                parameters.put("startedAt", startedAt.toString());
                parameters.put("completedAt", completedAt.toString());
                var result = transaction.run(query, parameters);
                if (!result.hasNext()) {
                    throw new IllegalArgumentException("A execução não pertence ao usuário");
                }
                result.consume();
                return null;
            });
        }
    }

    public void recordToolCall(
            UUID runId,
            String ownerSubject,
            String toolCallId,
            String toolName,
            String arguments,
            String responseData,
            boolean successful
    ) {
        String query = """
                MATCH (run:AgentRun {id: $runId, ownerSubject: $ownerSubject})
                MERGE (call:AgentToolCall {id: $callId})
                ON CREATE SET call.toolName = $toolName,
                              call.runId = $runId,
                              call.toolCallId = $toolCallId,
                              call.arguments = $arguments,
                              call.requestedAt = $requestedAt
                SET call.responseData = $responseData,
                    call.successful = $successful,
                    call.status = CASE WHEN $successful THEN 'SUCCEEDED' ELSE 'FAILED' END,
                    call.completedAt = $completedAt,
                    run.toolCalls = run.toolCalls + CASE WHEN $successful THEN 1 ELSE 0 END,
                    run.version = run.version + 1
                MERGE (run)-[:POSSUI_TOOL_CALL]->(call)
                RETURN call
                """;

        try (var session = driver.session()) {
            session.executeWrite(transaction -> {
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("runId", runId.toString());
                parameters.put("ownerSubject", ownerSubject);
                parameters.put("callId", toolCallKey(runId, toolCallId));
                parameters.put("toolCallId", toolCallId);
                parameters.put("toolName", toolName);
                parameters.put("arguments", arguments == null ? "" : arguments);
                parameters.put("responseData", responseData == null ? "" : responseData);
                parameters.put("successful", successful);
                parameters.put("requestedAt", Instant.now().toString());
                parameters.put("completedAt", Instant.now().toString());
                var result = transaction.run(query, parameters);
                if (!result.hasNext()) {
                    throw new IllegalArgumentException("A execução não pertence ao usuário");
                }
                result.consume();
                return null;
            });
        }
    }

    public boolean reserveToolCall(
            UUID runId,
            String ownerSubject,
            String toolCallId,
            String toolName,
            String arguments
    ) {
        String query = """
                MATCH (run:AgentRun {id: $runId, ownerSubject: $ownerSubject})
                MERGE (call:AgentToolCall {id: $callId})
                ON CREATE SET call.runId = $runId,
                              call.toolCallId = $toolCallId,
                              call.toolName = $toolName,
                              call.arguments = $arguments,
                              call.status = 'RESERVED',
                              call.requestedAt = $requestedAt
                WITH run, call
                WHERE call.status = 'RESERVED'
                SET call.status = 'EXECUTING',
                    call.startedAt = $startedAt,
                    run.version = run.version + 1
                MERGE (run)-[:POSSUI_TOOL_CALL]->(call)
                RETURN call
                """;

        try (var session = driver.session()) {
            return session.executeWrite(transaction -> {
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("runId", runId.toString());
                parameters.put("ownerSubject", ownerSubject);
                parameters.put("callId", toolCallKey(runId, toolCallId));
                parameters.put("toolCallId", toolCallId);
                parameters.put("toolName", toolName);
                parameters.put("arguments", arguments == null ? "" : arguments);
                parameters.put("requestedAt", Instant.now().toString());
                parameters.put("startedAt", Instant.now().toString());
                var result = transaction.run(query, parameters);
                return result.hasNext();
            });
        }
    }

    public void complete(UUID runId, String ownerSubject, String result, Instant completedAt) {
        updateTerminal(runId, ownerSubject, AgentRunStatus.COMPLETED, result, null, completedAt);
    }

    public void fail(UUID runId, String ownerSubject, String reason, Instant completedAt) {
        updateTerminal(runId, ownerSubject, AgentRunStatus.FAILED, null, reason, completedAt);
    }

    public void waitForCapacity(UUID runId, String ownerSubject, String reason) {
        String query = """
                MATCH (run:AgentRun {id: $runId, ownerSubject: $ownerSubject})
                SET run.status = 'WAITING_FOR_CAPACITY',
                    run.failureReason = $reason,
                    run.version = run.version + 1
                RETURN run
                """;
        executeUpdate(query, Map.of("runId", runId.toString(), "ownerSubject", ownerSubject, "reason", reason));
    }

    public void waitForUser(
            UUID runId,
            String ownerSubject,
            String waitingType,
            String message,
            String toolCallId,
            String toolName,
            String arguments
    ) {
        String query = """
                MATCH (run:AgentRun {id: $runId, ownerSubject: $ownerSubject})
                SET run.status = 'WAITING_FOR_USER',
                    run.result = $message,
                    run.failureReason = $waitingType,
                    run.waitingToolCallId = $toolCallId,
                    run.waitingToolName = $toolName,
                    run.waitingToolArguments = $arguments,
                    run.version = run.version + 1
                RETURN run
                """;
        executeUpdate(query, Map.of(
                "runId", runId.toString(),
                "ownerSubject", ownerSubject,
                "waitingType", waitingType,
                "message", message,
                "toolCallId", toolCallId == null ? "" : toolCallId,
                "toolName", toolName == null ? "" : toolName,
                "arguments", arguments == null ? "" : arguments
        ));
    }

    private void updateTerminal(
            UUID runId,
            String ownerSubject,
            AgentRunStatus status,
            String result,
            String failureReason,
            Instant completedAt
    ) {
        String query = """
                MATCH (run:AgentRun {id: $runId, ownerSubject: $ownerSubject})
                SET run.status = $status,
                    run.result = $result,
                    run.failureReason = $failureReason,
                    run.completedAt = $completedAt,
                    run.version = run.version + 1
                RETURN run
                """;
        executeUpdate(query, Map.of(
                "runId", runId.toString(),
                "ownerSubject", ownerSubject,
                "status", status.name(),
                "result", result == null ? "" : result,
                "failureReason", failureReason == null ? "" : failureReason,
                "completedAt", completedAt.toString()
        ));
    }

    private void executeUpdate(String query, Map<String, Object> parameters) {
        try (var session = driver.session()) {
            session.executeWrite(transaction -> {
                var result = transaction.run(query, parameters);
                if (!result.hasNext()) {
                    throw new IllegalArgumentException("A execução não pertence ao usuário");
                }
                result.consume();
                return null;
            });
        }
    }

    private AgentRun mapRun(Record record) {
        Map<String, Object> values = record.get("run").asMap();
        return new AgentRun(
                UUID.fromString(string(values, "id")),
                UUID.fromString(string(values, "subjectId")),
                string(values, "conversationId"),
                string(values, "ownerSubject"),
                AgentRunTrigger.valueOf(string(values, "trigger")),
                AgentRunStatus.valueOf(string(values, "status")),
                integer(values, "version"),
                integer(values, "steps"),
                integer(values, "modelInvocations"),
                integer(values, "toolCalls"),
                Instant.parse(string(values, "createdAt")),
                instant(values, "startedAt"),
                instant(values, "completedAt"),
                optionalString(values, "result"),
                optionalString(values, "failureReason")
        );
    }

    private Subject mapSubject(Record record) {
        Map<String, Object> values = record.get("subject").asMap();
        return new Subject(
                UUID.fromString(string(values, "id")),
                string(values, "ownerSubject"),
                string(values, "kind"),
                string(values, "title"),
                instant(values, "createdAt")
        );
    }

    private static String string(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            throw new IllegalStateException("Propriedade ausente no AgentRun: " + key);
        }
        return value.toString();
    }

    private static String optionalString(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString();
    }

    private static int integer(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
    }

    private static String toolCallKey(UUID runId, String toolCallId) {
        return runId + ":" + toolCallId;
    }

    private static Instant instant(Map<String, Object> values, String key) {
        String value = optionalString(values, key);
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }
}
