package com.pictet.adventurebook.validation;

import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.SectionType;
import org.junit.jupiter.api.Test;

import static com.pictet.adventurebook.validation.BookTestFixtures.book;
import static com.pictet.adventurebook.validation.BookTestFixtures.optionTo;
import static com.pictet.adventurebook.validation.BookTestFixtures.section;
import static org.assertj.core.api.Assertions.assertThat;

class ValidNextSectionIdRuleTest {

    private final ValidNextSectionIdRule rule = new ValidNextSectionIdRule();

    @Test
    void passesWhenEveryGotoIdExists() {
        Book book = book(
                section(1, SectionType.BEGIN, optionTo(2)),
                section(2, SectionType.END)
        );

        assertThat(rule.check(book)).isEmpty();
    }

    @Test
    void failsWhenAnOptionPointsToAMissingSection() {
        Book book = book(
                section(1, SectionType.BEGIN, optionTo(99)),
                section(2, SectionType.END)
        );

        assertThat(rule.check(book))
                .containsExactly("Section 1 has an option pointing to non-existent section 99");
    }

    @Test
    void reportsEveryBrokenReferenceNotJustTheFirst() {
        Book book = book(
                section(1, SectionType.BEGIN, optionTo(97), optionTo(98)),
                section(2, SectionType.END)
        );

        assertThat(rule.check(book)).hasSize(2);
    }
}
