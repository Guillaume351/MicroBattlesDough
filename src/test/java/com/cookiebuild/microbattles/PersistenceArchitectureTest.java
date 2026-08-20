package com.cookiebuild.microbattles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PersistenceArchitectureTest {
    @Test
    void gameplayAndKitUiDoNotOwnPersistenceContextsOrBlockAdmission() throws IOException {
        String game = source("game/MicroBattlesGame.java");
        String javaUi = source("ui/KitSelectionUI.java");
        String bedrockUi = source("ui/bedrock/BedrockKitSelectionUI.java");

        assertFalse(game.contains("GenericDAOImpl"));
        assertFalse(game.contains("EntityManager"));
        assertTrue(game.contains("runTaskAsynchronously"));
        assertTrue(game.contains("BoundedAsyncFlush.runAndAwait"));
        assertTrue(javaUi.contains("runTaskAsynchronously"));
        assertTrue(bedrockUi.contains("runTaskAsynchronously"));
        assertTrue(javaUi.contains("tryBeginMutation"));
        assertTrue(bedrockUi.contains("tryBeginMutation"));
        String shutdownFlush = game.substring(game.indexOf("private void persistInterruptedOutcome"),
                game.indexOf("private Map<String, Object> interruptedPerformanceMetrics"));
        assertFalse(shutdownFlush.contains("getScheduler"));
        assertTrue(shutdownFlush.contains("new MatchService(null)"));
    }

    private static String source(String relative) throws IOException {
        Path base = Path.of("src/main/java/com/cookiebuild/microbattles");
        if (!Files.exists(base)) base = Path.of("MicroBattles").resolve(base);
        return Files.readString(base.resolve(relative));
    }
}
