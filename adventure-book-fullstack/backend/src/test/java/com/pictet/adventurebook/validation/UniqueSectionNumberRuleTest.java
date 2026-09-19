package com.pictet.adventurebook.validation;

import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.SectionType;
import org.junit.jupiter.api.Test;

import static com.pictet.adventurebook.validation.BookTestFixtures.book;
import static com.pictet.adventurebook.validation.BookTestFixtures.optionTo;
import static com.pictet.adventurebook.validation.BookTestFixtures.section;
import static org.assertj.core.api.Assertions.assertThat;

class UniqueSectionNumberRuleTest {

    private final UniqueSectionNumberRule rule = new UniqueSectionNumberRule();

    @Test
    void passesWhenEverySectionNumberIsUnique() {
        Book book = book(
                section(1, SectionType.BEGIN, optionTo(2)),
                section(2, SectionType.END)
        );

        assertThat(rule.check(book)).isEmpty();
    }

    @Test
    void failsWhenTwoSectionsShareANumber() {
        Book book = book(
                section(1, SectionType.BEGIN, optionTo(7)),
                section(7, SectionType.NODE, optionTo(7)),
                section(7, SectionType.END)
        );

        assertThat(rule.check(book)).containsExactly("Section number 7 is used by 2 sections");
    }
}
