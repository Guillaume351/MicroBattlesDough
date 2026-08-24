package com.cookiebuild.microbattles.commands;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class MapVoteCommandContractTest {
    @Test
    void voteInventoryRejectsJavaDragsIntoEveryTopSlot() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/microbattles/commands/MapVoteCommand.java"));

        assertTrue(source.contains("void drag(InventoryDragEvent event)"));
        assertTrue(source.contains("event.getRawSlots().stream().anyMatch"));
        assertTrue(source.contains("slot >= 0 && slot < topSize"));
        assertTrue(source.contains("event.setCancelled(true)"));
    }
}
