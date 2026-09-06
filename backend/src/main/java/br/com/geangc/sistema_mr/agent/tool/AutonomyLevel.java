package br.com.geangc.sistema_mr.agent.tool;

public enum AutonomyLevel {
    OBSERVE(0),
    SUGGEST(1),
    ASK(-1),
    SCHEDULE(2),
    EXECUTE(3),
    CONFIRM(-1);

    private final int actionRank;

    AutonomyLevel(int actionRank) {
        this.actionRank = actionRank;
    }

    public boolean permits(AutonomyLevel required) {
        if (required == ASK || required == CONFIRM) {
            return false;
        }
        return actionRank >= required.actionRank;
    }
}
