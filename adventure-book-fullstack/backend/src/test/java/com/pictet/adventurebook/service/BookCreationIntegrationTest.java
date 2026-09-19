package com.pictet.adventurebook.service;

import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.Option;
import com.pictet.adventurebook.domain.Section;
import com.pictet.adventurebook.domain.SectionType;
import com.pictet.adventurebook.exception.BookValidationException;
import com.pictet.adventurebook.domain.GameSession;
import com.pictet.adventurebook.repository.BookRepository;
import com.pictet.adventurebook.repository.GameSessionRepository;
import com.pictet.adventurebook.validation.BookValidator;
import com.pictet.adventurebook.validation.HasEndingRule;
import com.pictet.adventurebook.validation.NonEndingHasOptionsRule;
import com.pictet.adventurebook.validation.SingleBeginningRule;
import com.pictet.adventurebook.validation.UniqueSectionNumberRule;
import com.pictet.adventurebook.validation.ValidNextSectionIdRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link BookService#create} against a real {@link BookValidator} (all five
 * rules, not mocked) and a real H2-backed {@link BookRepository} — the thing that actually
 * matters for Objective 5 is that an invalid submission never reaches the database and a
 * valid one comes back out exactly as submitted, which a mocked validator can't prove.
 */
@DataJpaTest
class BookCreationIntegrationTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    private BookService bookService;

    @BeforeEach
    void setUp() {
        BookValidator validator = new BookValidator(List.of(
                new SingleBeginningRule(),
                new HasEndingRule(),
                new ValidNextSectionIdRule(),
                new NonEndingHasOptionsRule(),
                new UniqueSectionNumberRule()
        ));
        bookService = new BookService(bookRepository, gameSessionRepository, validator);
    }

    @Test
    void aWellFormedSubmittedBookIsPersisted() {
        Book book = new Book("Reader-submitted book", "A Contributor", "MEDIUM", List.of(
                new Section(1, "Start", SectionType.BEGIN, List.of(new Option("Go to 2", 2, null))),
                new Section(2, "End", SectionType.END, List.of())
        ));

        Book saved = bookService.create(book);

        assertThat(saved.getId()).isNotNull();
        assertThat(bookRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void aSubmittedBookWithNoBeginningIsRejectedAndNeverPersisted() {
        Book book = new Book("Broken book", "A Contributor", "MEDIUM", List.of(
                new Section(1, "Only node", SectionType.NODE, List.of(new Option("Go to 1", 1, null)))
        ));

        assertThatThrownBy(() -> bookService.create(book))
                .isInstanceOf(BookValidationException.class);
        assertThat(bookRepository.count()).isZero();
    }

    @Test
    void aSubmittedBookWithADanglingGotoIdIsRejectedWithAClearReason() {
        Book book = new Book("Broken book", "A Contributor", "MEDIUM", List.of(
                new Section(1, "Start", SectionType.BEGIN, List.of(new Option("Go to nowhere", 99, null))),
                new Section(2, "End", SectionType.END, List.of())
        ));

        assertThatThrownBy(() -> bookService.create(book))
                .isInstanceOf(BookValidationException.class)
                .satisfies(ex -> assertThat(((BookValidationException) ex).getErrors())
                        .containsExactly("Section 1 has an option pointing to non-existent section 99"));
    }

    private Book twoSectionBook(String title) {
        return new Book(title, "A Contributor", "EASY", List.of(
                new Section(1, "Start", SectionType.BEGIN, List.of(new Option("Go to 2", 2, null))),
                new Section(2, "End", SectionType.END, List.of())
        ));
    }

    // The mocked service test proves the book object is revised; only a real persistence
    // context proves the sections it replaced actually leave the database. Assigning a new
    // list instead of mutating the tracked one would leave the old rows orphaned here.
    @Test
    void revisingABookDeletesTheSectionsItReplaced() {
        Book saved = bookService.create(twoSectionBook("First draft"));
        Long bookId = saved.getId();

        Book revision = new Book("Second draft", "A Contributor", "HARD", List.of(
                new Section(10, "A new start", SectionType.BEGIN, List.of(new Option("Go to 20", 20, null))),
                new Section(20, "A new end", SectionType.END, List.of())
        ));
        bookService.update(bookId, revision);

        Book reloaded = bookRepository.findWithSectionsById(bookId).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Second draft");
        assertThat(reloaded.getSections()).extracting(Section::getSectionNumber)
                .containsExactlyInAnyOrder(10L, 20L);
    }

    // The common case: fixing a typo without renumbering anything. The old sections have to
    // be deleted before the new ones are inserted, or the unique (book_id, section_number)
    // constraint fires — which is exactly what happened the first time this was written.
    @Test
    void revisingABookThatKeepsTheSameSectionNumbers() {
        Book saved = bookService.create(twoSectionBook("With a typo"));
        Long bookId = saved.getId();

        Book revision = new Book("Typo fixed", "A Contributor", "EASY", List.of(
                new Section(1, "A corrected start", SectionType.BEGIN, List.of(new Option("Go to 2", 2, null))),
                new Section(2, "A corrected end", SectionType.END, List.of())
        ));
        bookService.update(bookId, revision);

        Book reloaded = bookRepository.findWithSectionsById(bookId).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Typo fixed");
        assertThat(reloaded.getSections()).extracting(Section::getSectionNumber)
                .containsExactlyInAnyOrder(1L, 2L);
        assertThat(reloaded.findBySectionNumber(1).orElseThrow().getText()).isEqualTo("A corrected start");
    }

    @Test
    void revisingABookIntoAnInvalidOneChangesNothing() {
        Book saved = bookService.create(twoSectionBook("Still fine"));
        Long bookId = saved.getId();

        Book broken = new Book("Broken revision", "A Contributor", "EASY", List.of(
                new Section(1, "Nowhere to go", SectionType.NODE, List.of())
        ));

        assertThatThrownBy(() -> bookService.update(bookId, broken))
                .isInstanceOf(BookValidationException.class);
    }

    @Test
    void deletingABookRemovesItAndTheGamesPlayedOnIt() {
        Book saved = bookService.create(twoSectionBook("To be removed"));
        Long bookId = saved.getId();
        Book playable = bookRepository.findWithSectionsById(bookId).orElseThrow();
        gameSessionRepository.save(GameSession.start(playable, "test-player"));

        assertThat(gameSessionRepository.findByBookId(bookId)).hasSize(1);

        bookService.delete(bookId);

        assertThat(bookRepository.findById(bookId)).isEmpty();
        assertThat(gameSessionRepository.findByBookId(bookId)).isEmpty();
    }
}
