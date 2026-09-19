package com.pictet.adventurebook.web.dto;

import com.pictet.adventurebook.domain.Book;

/**
 * Converts a {@link Book} entity into the flat shape the library listing exposes.
 * A plain static method rather than a mapping library, since there's only one field
 * shape to produce and it isn't going to grow much.
 */
public final class BookSummaryMapper {

    private BookSummaryMapper() {
    }

    /**
     * @param sectionCount passed in rather than read from {@code book.getSections()} — the
     *                     listing loads books without their sections on purpose, so asking
     *                     the entity would trigger a lazy load per row.
     */
    public static BookSummaryDto toSummary(Book book, int sectionCount) {
        return new BookSummaryDto(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getDifficulty(),
                sectionCount
        );
    }
}
