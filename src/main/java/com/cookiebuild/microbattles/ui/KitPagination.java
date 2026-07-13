package com.cookiebuild.microbattles.ui;

import java.util.List;

public final class KitPagination {
    private KitPagination() {
    }

    public static int pageCount(int itemCount, int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        return Math.max(1, (itemCount + pageSize - 1) / pageSize);
    }

    public static <T> List<T> page(List<T> items, int requestedPage, int pageSize) {
        int pageCount = pageCount(items.size(), pageSize);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int from = page * pageSize;
        int to = Math.min(items.size(), from + pageSize);
        return List.copyOf(items.subList(from, to));
    }
}
