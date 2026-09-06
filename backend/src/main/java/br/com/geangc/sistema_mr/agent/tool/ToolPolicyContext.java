package br.com.geangc.sistema_mr.agent.tool;

import br.com.geangc.sistema_mr.agent.model.DataConstraints;
import java.util.Set;
import java.util.UUID;

public record ToolPolicyContext(
        UUID runId,
        UUID subjectId,
        String ownerSubject,
        DataConstraints dataConstraints,
        AutonomyLevel grantedAutonomy,
        Set<String> permissions
) {
    public ToolPolicyContext {
        dataConstraints = dataConstraints == null ? DataConstraints.unspecified() : dataConstraints;
        grantedAutonomy = grantedAutonomy == null ? AutonomyLevel.OBSERVE : grantedAutonomy;
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
