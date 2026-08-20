package com.cookiebuild.microbattles.game;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/** Fair timeout ranking: survivors, team eliminations, kills, then remaining health; exact ties draw. */
public final class MicroBattlesTimeoutPolicy {
    public record TeamStanding(String team, int alivePlayers, int teamsEliminated, int kills,
            double remainingHealth) {
    }

    private static final Comparator<TeamStanding> RANKING = Comparator
            .comparingInt(TeamStanding::alivePlayers)
            .thenComparingInt(TeamStanding::teamsEliminated)
            .thenComparingInt(TeamStanding::kills)
            .thenComparingDouble(TeamStanding::remainingHealth);

    private MicroBattlesTimeoutPolicy() {
    }

    public static Optional<String> winner(Collection<TeamStanding> standings) {
        if (standings == null || standings.isEmpty()) return Optional.empty();
        TeamStanding best = standings.stream().max(RANKING).orElseThrow();
        long tied = standings.stream().filter(candidate -> sameScore(candidate, best)).count();
        return tied == 1 ? Optional.of(best.team()) : Optional.empty();
    }

    private static boolean sameScore(TeamStanding left, TeamStanding right) {
        return left.alivePlayers() == right.alivePlayers()
                && left.teamsEliminated() == right.teamsEliminated()
                && left.kills() == right.kills()
                && Double.compare(left.remainingHealth(), right.remainingHealth()) == 0;
    }
}
