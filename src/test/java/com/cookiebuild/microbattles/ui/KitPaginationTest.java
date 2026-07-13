package com.cookiebuild.microbattles.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class KitPaginationTest {
    @Test
    void paginatesAndClampsRequestedPage() {
        List<Integer> values = IntStream.range(0, 49).boxed().toList();
        assertEquals(2, KitPagination.pageCount(values.size(), 36));
        assertEquals(36, KitPagination.page(values, 0, 36).size());
        assertEquals(List.of(36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48),
                KitPagination.page(values, 99, 36));
    }

    @Test
    void rejectsInvalidPageSize() {
        assertThrows(IllegalArgumentException.class, () -> KitPagination.pageCount(1, 0));
    }
}
