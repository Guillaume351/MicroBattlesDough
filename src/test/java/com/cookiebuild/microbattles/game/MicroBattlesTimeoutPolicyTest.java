package com.cookiebuild.microbattles.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class MicroBattlesTimeoutPolicyTest {
    @Test
    void ranksSurvivorsThenTeamEliminationsKillsAndHealth() {
        assertEquals("Blue", MicroBattlesTimeoutPolicy.winner(List.of(
                new MicroBattlesTimeoutPolicy.TeamStanding("Blue", 2, 0, 0, 5),
                new MicroBattlesTimeoutPolicy.TeamStanding("Red", 1, 9, 9, 20))).orElseThrow());
        assertEquals("Red", MicroBattlesTimeoutPolicy.winner(List.of(
                new MicroBattlesTimeoutPolicy.TeamStanding("Blue", 2, 0, 9, 20),
                new MicroBattlesTimeoutPolicy.TeamStanding("Red", 2, 1, 0, 2))).orElseThrow());
    }

    @Test
    void exactTieIsAlwaysADraw() {
        assertTrue(MicroBattlesTimeoutPolicy.winner(List.of(
                new MicroBattlesTimeoutPolicy.TeamStanding("Blue", 1, 1, 2, 12),
                new MicroBattlesTimeoutPolicy.TeamStanding("Red", 1, 1, 2, 12))).isEmpty());
    }
}
