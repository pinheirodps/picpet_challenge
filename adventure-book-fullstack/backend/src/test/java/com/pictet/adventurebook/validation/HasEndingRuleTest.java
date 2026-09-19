package com.pictet.adventurebook.validation;

import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.SectionType;
import org.junit.jupiter.api.Test;

import static com.pictet.adventurebook.validation.BookTestFixtures.book;
import static com.pictet.adventurebook.validation.BookTestFixtures.optionTo;
import static com.pictet.adventurebook.validation.BookTestFixtures.section;
import static org.assertj.core.api.Assertions.assertThat;

class HasEndingRuleTest {

    private final HasEndingRule rule = new HasEndingRule();

    @Test
    void passesWithOneEnding() {
        Book book = book(
                section(1, SectionType.BEGIN, optionTo(2)),
                section(2, SectionType.END)
        );

        assertThat(rule.check(book)).isEmpty();
    }

    @Test
    void passesWithMultipleEndings() {
        Book book = book(
                section(1, SectionType.BEGIN, optionTo(2), optionTo(3)),
                section(2, SectionType.END),
                section(3, SectionType.END)
        );

        assertThat(rule.check(book)).isEmpty();
    }

    @Test
    void failsWithNoEnding() {
        Book book = book(
                section(1, SectionType.BEGIN, optionTo(2)),
                section(2, SectionType.NODE, optionTo(1))
        );

        assertThat(rule.check(book)).containsExactly("Book has no ending section");
    }
}
