package com.pictet.adventurebook.repository;

import com.pictet.adventurebook.domain.Book;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the specifications against a real (in-memory H2) JPA context, since what they
 * actually need to prove is that the generated SQL matches the right rows — a plain unit
 * test on the lambda wouldn't exercise that.
 */
@DataJpaTest
class BookSpecificationsTest {

    @Autowired
    private BookRepository bookRepository;

    @Test
    void titleOrAuthorContainsMatchesEitherFieldCaseInsensitively() {
        bookRepository.save(new Book("The Crystal Caverns", "Evelyn Stormrider", "EASY", List.of()));
        bookRepository.save(new Book("Dragon Quest", "Rowan Ashfell", "HARD", List.of()));

        List<Book> byTitle = bookRepository.findAll(BookSpecifications.titleOrAuthorContains("crystal"));
        List<Book> byAuthor = bookRepository.findAll(BookSpecifications.titleOrAuthorContains("ashfell"));
        List<Book> noMatch = bookRepository.findAll(BookSpecifications.titleOrAuthorContains("nonexistent"));

        assertThat(byTitle).extracting(Book::getTitle).containsExactly("The Crystal Caverns");
        assertThat(byAuthor).extracting(Book::getTitle).containsExactly("Dragon Quest");
        assertThat(noMatch).isEmpty();
    }

    @Test
    void hasDifficultyMatchesExactlyIgnoringCase() {
        bookRepository.save(new Book("The Crystal Caverns", "Evelyn Stormrider", "EASY", List.of()));
        bookRepository.save(new Book("Dragon Quest", "Rowan Ashfell", "HARD", List.of()));

        List<Book> easyBooks = bookRepository.findAll(BookSpecifications.hasDifficulty("easy"));

        assertThat(easyBooks).extracting(Book::getTitle).containsExactly("The Crystal Caverns");
    }

    @Test
    void blankFiltersMatchEverything() {
        bookRepository.save(new Book("The Crystal Caverns", "Evelyn Stormrider", "EASY", List.of()));

        List<Book> all = bookRepository.findAll(
                Specification.where(BookSpecifications.titleOrAuthorContains(null))
                        .and(BookSpecifications.hasDifficulty(null)));

        assertThat(all).hasSize(1);
    }
}
