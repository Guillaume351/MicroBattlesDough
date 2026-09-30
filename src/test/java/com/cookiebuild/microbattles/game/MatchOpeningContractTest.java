package com.cookiebuild.microbattles.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class MatchOpeningContractTest {
    @Test
    void loneTeamIsSplitBeforePlayersAreTeleportedAndSingleTeamMatchesAwardNoWin() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/microbattles/game/MicroBattlesGame.java"));
        String start = source.substring(source.indexOf("public void startGame()"));
        assertTrue(start.indexOf("splitLoneTeamForOpening();") < start.indexOf("super.startGame();"));
        assertTrue(start.indexOf("teamsRepresentedAtStart =") < start.indexOf("super.startGame();"));
        assertTrue(source.contains("TeamComposition.canFieldOpposingTeams(teamCounts())"));

        String end = source.substring(source.indexOf("private void endGame(MicroBattlesTeam winningTeam)"));
        assertTrue(end.indexOf("TeamComposition.canAwardWin(teamsRepresentedAtStart)")
                < end.indexOf("persistOutcomeAndRewards(winnerIds)"));
    }

    @Test
    void voteAnnouncementAndHowToPlayAreLocalizedPlainText() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/microbattles/game/MicroBattlesGame.java"));
        String announce = source.substring(source.indexOf("private void announceMapVote(Player player)"),
                source.indexOf("private List<Integer> teamCounts()"));
        assertTrue(announce.contains("\"microbattles.map_vote.announce\""));
        assertFalse(announce.contains("clickEvent"));
        assertTrue(source.contains("\"microbattles.howto.title\""));
        assertTrue(source.contains("\"microbattles.howto.subtitle\""));
    }

    @Test
    void voteMenusShowReadableNamesAndVoidUsesTheMapLevel() throws Exception {
        String vote = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/microbattles/commands/MapVoteCommand.java"));
        assertFalse(vote.contains("Component.text(map.getKey()"));
        assertFalse(vote.contains("BedrockButtonText.format(map.getKey()"));
        assertTrue(vote.contains("MapManager.getMapDisplayName("));

        String listener = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/microbattles/listener/InGamePlayerEventListener.java"));
        assertFalse(listener.contains("getY() < 0"));
        assertTrue(listener.contains("microBattlesGame.isBelowVoid(player.getLocation().getY())"));
    }
}
