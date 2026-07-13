package com.cookiebuild.microbattles.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class NextMapVoteTest {
    @Test
    void oneVotePerPlayerAndWinnerIsConsumed() {
        NextMapVote vote = new NextMapVote();
        vote.configure(List.of("game-1", "game-2"));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertTrue(vote.vote(first, "GAME-1"));
        assertTrue(vote.vote(first, "game-2"));
        assertTrue(vote.vote(second, "game-2"));
        assertEquals("game-2", vote.consumeWinner());
        assertNull(vote.consumeWinner());
    }

    @Test
    void rejectsUnknownMapAndBreaksTiesByName() {
        NextMapVote vote = new NextMapVote();
        vote.configure(List.of("beta", "alpha"));
        assertFalse(vote.vote(UUID.randomUUID(), "gamma"));
        vote.vote(UUID.randomUUID(), "beta");
        vote.vote(UUID.randomUUID(), "alpha");
        assertEquals("alpha", vote.consumeWinner());
    }

    @Test
    void disconnectedVoteCanBeRemoved() {
        NextMapVote vote = new NextMapVote();
        vote.configure(List.of("game-1"));
        UUID player = UUID.randomUUID();
        vote.vote(player, "game-1");
        vote.removeVote(player);
        assertNull(vote.consumeWinner());
    }
}
