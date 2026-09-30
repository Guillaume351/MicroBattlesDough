package com.cookiebuild.microbattles.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class TeamCompositionTest {
    @Test
    void loneTwoPlayerPartyIsSplitIntoADuel() {
        List<Integer> loneParty = List.of(2, 0, 0, 0);

        assertEquals(1, TeamComposition.representedTeams(loneParty));
        assertTrue(TeamComposition.canFieldOpposingTeams(loneParty));
        assertEquals(Optional.of(new TeamComposition.Split(0, 1, 1)), TeamComposition.openingSplit(loneParty));
    }

    @Test
    void loneThreePlayerPartyMovesOneMemberToTheFirstEmptyTeam() {
        assertEquals(Optional.of(new TeamComposition.Split(1, 0, 1)),
                TeamComposition.openingSplit(List.of(0, 3, 0, 0)));
    }

    @Test
    void partyGroupingIsKeptWhenOtherPlayersArePresent() {
        List<Integer> partyAndSolo = List.of(2, 1, 0, 0);

        assertEquals(2, TeamComposition.representedTeams(partyAndSolo));
        assertTrue(TeamComposition.canFieldOpposingTeams(partyAndSolo));
        assertEquals(Optional.empty(), TeamComposition.openingSplit(partyAndSolo));
    }

    @Test
    void aSinglePlayerCannotFieldOpposingTeams() {
        assertFalse(TeamComposition.canFieldOpposingTeams(List.of(1, 0, 0, 0)));
        assertFalse(TeamComposition.canFieldOpposingTeams(List.of(0, 0, 0, 0)));
        assertEquals(Optional.empty(), TeamComposition.openingSplit(List.of(1, 0, 0, 0)));
    }

    @Test
    void winsAreOnlyAwardedWhenAtLeastTwoTeamsStarted() {
        assertFalse(TeamComposition.canAwardWin(0));
        assertFalse(TeamComposition.canAwardWin(1));
        assertTrue(TeamComposition.canAwardWin(2));
        assertTrue(TeamComposition.canAwardWin(4));
    }
}
