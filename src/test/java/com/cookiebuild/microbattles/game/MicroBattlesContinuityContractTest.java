package com.cookiebuild.microbattles.game;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class MicroBattlesContinuityContractTest {
    @Test
    void liveDisconnectIsReservedAndViewerCleanupPrecedesWorldUnload() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/microbattles/game/MicroBattlesGame.java"));
        String removal = source.substring(source.indexOf("public synchronized void removePlayer(CookiePlayer player, String reason)"));
        assertTrue(source.contains("implements ReconnectableGame"));
        assertTrue(source.contains("PlayerActivitySnapshot.capture"));
        assertTrue(source.indexOf("snapshot.relocate") < source.indexOf("restorePlayerAfterReconnect(cookiePlayer)"));
        assertTrue(source.indexOf("restorePlayerAfterReconnect(cookiePlayer)") < source.indexOf("snapshot.applyState"));
        assertTrue(removal.indexOf("getSpectators().stream()")
                < removal.indexOf("\"disconnect\".equalsIgnoreCase(reason)"));
        assertTrue(source.indexOf("ejectSpectatorsToLobby();")
                < source.indexOf("MapManager.unloadMap(map)"));
    }
}
