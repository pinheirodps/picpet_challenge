package com.pictet.adventurebook.validation;

import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.SectionType;
import org.junit.jupiter.api.Test;

import static com.pictet.adventurebook.validation.BookTestFixtures.book;
import static com.pictet.adventurebook.validation.BookTestFixtures.optionTo;
import static com.pictet.adventurebook.validation.BookTestFixtures.section;
import static org.assertj.core.api.Assertions.assertThat;

class NonEndingHasOptionsRuleTest {

    private final NonEndingHasOptionsRule rule = new NonEndingHasOptionsRule();

    @Test
    void passesWhenEveryNonEndingSectionHasOptions() {
        Book book = book(
                section(1, SectionType.BEGIN, optionTo(2)),
                section(2, SectionType.END)
        );

        assertThat(rule.check(book)).isEmpty();
    }

    @Test
    void endingSectionsAreAllowedToHaveNoOptions() {
        Book book = book(
                section(1, SectionType.BEGIN, optionTo(2)),
                section(2, SectionType.END)
        );

        assertThat(rule.check(book)).isEmpty();
    }

    @Test
    void failsWhenANodeSectionHasNoOptions() {
        Book book = book(
                section(1, SectionType.BEGIN, optionTo(2)),
                section(2, SectionType.NODE)
        );

        assertThat(rule.check(book))
                .containsExactly("Section 2 is not an ending but has no options");
    }
}
