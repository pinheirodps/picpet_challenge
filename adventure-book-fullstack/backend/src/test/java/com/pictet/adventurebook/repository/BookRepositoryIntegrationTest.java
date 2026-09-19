package com.pictet.adventurebook.repository;

import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.Option;
import com.pictet.adventurebook.domain.Section;
import com.pictet.adventurebook.domain.SectionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loading a book's sections and options used to go through one query with a fetch join on
 * both collections, which silently duplicated every section once per option belonging to
 * any of its siblings (a cross product between the two collections that {@code distinct}
 * on the root entity doesn't undo). This test loads a book shaped enough to trigger that —
 * several sections, several options each — and pins down that {@link
 * BookRepository#findWithSectionsById} plus {@link
 * BookRepository#findSectionsWithOptionsByBookId} returns each section exactly once.
 */
@DataJpaTest
class BookRepositoryIntegrationTest {

    @Autowired
    private BookRepository bookRepository;

    @Test
    void loadingSectionsAndOptionsDoesNotDuplicateSections() {
        Section begin = new Section(1, "Start", SectionType.BEGIN, List.of(
                new Option("Go to 2", 2, null),
                new Option("Go to 3", 3, null)
        ));
        Section middle = new Section(2, "Middle", SectionType.NODE, List.of(
                new Option("Go to 3", 3, null)
        ));
        Section end = new Section(3, "End", SectionType.END, List.of());

        Book book = new Book("Test Book", "Test Author", "EASY", List.of(begin, middle, end));
        book = bookRepository.save(book);

        Book loaded = bookRepository.findWithSectionsById(book.getId()).orElseThrow();
        List<Section> sectionsWithOptions = bookRepository.findSectionsWithOptionsByBookId(book.getId());

        assertThat(loaded.getSections()).hasSize(3);
        assertThat(loaded.getSections()).extracting(Section::getSectionNumber)
                .containsExactlyInAnyOrder(1L, 2L, 3L);

        Map<Long, Integer> optionCountsBySection = sectionsWithOptions.stream()
                .collect(Collectors.toMap(Section::getSectionNumber, s -> s.getOptions().size()));
        assertThat(optionCountsBySection).containsEntry(1L, 2).containsEntry(2L, 1).containsEntry(3L, 0);
    }
}
