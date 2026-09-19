package com.pictet.adventurebook.validation;

import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.SectionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.pictet.adventurebook.validation.BookTestFixtures.book;
import static com.pictet.adventurebook.validation.BookTestFixtures.optionTo;
import static com.pictet.adventurebook.validation.BookTestFixtures.section;
import static org.assertj.core.api.Assertions.assertThat;

class BookValidatorTest {

    private final BookValidator validator = new BookValidator(List.of(
            new SingleBeginningRule(),
            new HasEndingRule(),
            new ValidNextSectionIdRule(),
            new NonEndingHasOptionsRule(),
            new UniqueSectionNumberRule()
    ));

    @Test
    void aWellFormedBookIsValid() {
        Book book = book(
                section(1, SectionType.BEGIN, optionTo(2), optionTo(3)),
                section(2, SectionType.NODE, optionTo(3)),
                section(3, SectionType.END)
        );

        assertThat(validator.isValid(book)).isTrue();
        assertThat(validator.validate(book)).isEmpty();
    }

    @Test
    void collectsErrorsFromEveryFailingRuleAtOnce() {
        // No beginning, no ending, and section 2 is a dead end — three separate rules broken.
        Book book = book(
                section(1, SectionType.NODE, optionTo(2)),
                section(2, SectionType.NODE)
        );

        assertThat(validator.validate(book)).containsExactlyInAnyOrder(
                "Book has no beginning section",
                "Book has no ending section",
                "Section 2 is not an ending but has no options"
        );
    }

    @Test
    void anUnreachableDeadEndSectionMakesTheBookInvalid() {
        // Mirrors the sample data: an orphaned NODE section with no options, like section
        // 666 in the-prisoner.json and pirates-jade-sea.json.
        Book book = book(
                section(1, SectionType.BEGIN, optionTo(2)),
                section(2, SectionType.END),
                section(666, SectionType.NODE)
        );

        assertThat(validator.isValid(book)).isFalse();
        assertThat(validator.validate(book))
                .containsExactly("Section 666 is not an ending but has no options");
    }
}
