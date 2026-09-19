package com.pictet.adventurebook.validation;

import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.SectionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.pictet.adventurebook.validation.BookTestFixtures.book;
import static com.pictet.adventurebook.validation.BookTestFixtures.optionTo;
import static com.pictet.adventurebook.validation.BookTestFixtures.section;
import static org.assertj.core.api.Assertions.assertThat;

class SingleBeginningRuleTest {

    private final SingleBeginningRule rule = new SingleBeginningRule();

    @Test
    void passesWithExactlyOneBeginning() {
        Book book = book(
                section(1, SectionType.BEGIN, optionTo(2)),
                section(2, SectionType.END)
        );

        assertThat(rule.check(book)).isEmpty();
    }

    @Test
    void failsWithNoBeginning() {
        Book book = book(
                section(1, SectionType.NODE, optionTo(2)),
                section(2, SectionType.END)
        );

        assertThat(rule.check(book)).containsExactly("Book has no beginning section");
    }

    @Test
    void failsWithMoreThanOneBeginning() {
        Book book = book(
                section(1, SectionType.BEGIN, optionTo(3)),
                section(2, SectionType.BEGIN, optionTo(3)),
                section(3, SectionType.END)
        );

        assertThat(rule.check(book)).containsExactly("Book has more than one beginning section (2 found)");
    }
}
