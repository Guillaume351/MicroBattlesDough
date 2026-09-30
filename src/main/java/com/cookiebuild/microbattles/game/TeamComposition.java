package com.cookiebuild.microbattles.game;

import java.util.List;
import java.util.Optional;

/**
 * Pure team-composition rules. A match needs opposing teams: a party that is
 * alone in the lobby is split at start instead of forming one team that wins
 * instantly, and a match that still starts with a single team never awards a win.
 */
public final class TeamComposition {
    /** Moves {@code count} players from {@code fromTeam} to the empty {@code toTeam}. */
    public record Split(int fromTeam, int toTeam, int count) {
    }

    private TeamComposition() {
    }

    public static int representedTeams(List<Integer> teamCounts) {
        return (int) teamCounts.stream().filter(count -> count > 0).count();
    }

    /** True when the lobby already has opposing teams or its only team can be split into two. */
    public static boolean canFieldOpposingTeams(List<Integer> teamCounts) {
        return representedTeams(teamCounts) >= 2 || openingSplit(teamCounts).isPresent();
    }

    /** Splits a lone team of two or more players across an empty team; otherwise keeps grouping. */
    public static Optional<Split> openingSplit(List<Integer> teamCounts) {
        if (representedTeams(teamCounts) != 1) {
            return Optional.empty();
        }
        int fromTeam = -1;
        int toTeam = -1;
        for (int index = 0; index < teamCounts.size(); index++) {
            if (teamCounts.get(index) > 0) {
                fromTeam = index;
            } else if (toTeam < 0) {
                toTeam = index;
            }
        }
        int size = teamCounts.get(fromTeam);
        if (size < 2 || toTeam < 0) {
            return Optional.empty();
        }
        return Optional.of(new Split(fromTeam, toTeam, size / 2));
    }

    public static boolean canAwardWin(int teamsRepresentedAtStart) {
        return teamsRepresentedAtStart >= 2;
    }
}
