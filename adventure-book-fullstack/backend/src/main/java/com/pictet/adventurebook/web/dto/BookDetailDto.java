package com.pictet.adventurebook.web.dto;

import com.pictet.adventurebook.domain.ConsequenceType;
import com.pictet.adventurebook.domain.SectionType;

import java.util.List;

/**
 * A whole book, sections and options included — what the editor loads to revise an existing
 * book.
 *
 * <p>Deliberately mirrors {@link CreateBookRequest}'s shape rather than inventing its own:
 * the editor reads a book in this form, changes it, and submits it back as a
 * {@code CreateBookRequest}, so a round trip doesn't need a translation step that could drop
 * a field. {@code gameCount} is the one extra — the editor warns about games that an edit or
 * delete would end, and that number has to come from somewhere.
 */
public record BookDetailDto(
        Long id,
        String title,
        String author,
        String difficulty,
        long gamesInProgress,
        List<SectionDetail> sections
) {

    /** One section, with the book-local number the options refer to. */
    public record SectionDetail(long id, String text, SectionType type, List<OptionDetail> options) {
    }

    /** One choice; {@code consequence} is null when the choice is harmless. */
    public record OptionDetail(String description, long gotoId, ConsequenceDetail consequence) {
    }

    /** The health effect of a choice, in the same shape the editor submits it back in. */
    public record ConsequenceDetail(ConsequenceType type, int value, String text) {
    }
}
