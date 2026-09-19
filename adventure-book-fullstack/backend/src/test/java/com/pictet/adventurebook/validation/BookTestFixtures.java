package com.pictet.adventurebook.validation;

import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.Option;
import com.pictet.adventurebook.domain.Section;
import com.pictet.adventurebook.domain.SectionType;

import java.util.List;

/**
 * Small helpers for building {@link Book} instances in validation tests without repeating
 * the same constructor calls everywhere.
 */
final class BookTestFixtures {

    private BookTestFixtures() {
    }

    static Book book(Section... sections) {
        return new Book("Test Book", "Test Author", "EASY", List.of(sections));
    }

    static Section section(long number, SectionType type, Option... options) {
        return new Section(number, "Section " + number, type, List.of(options));
    }

    static Option optionTo(long gotoId) {
        return new Option("Go to " + gotoId, gotoId, null);
    }
}
