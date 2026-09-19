package com.pictet.adventurebook.service;

import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.GameSession;
import com.pictet.adventurebook.domain.GameStatus;
import com.pictet.adventurebook.domain.Option;
import com.pictet.adventurebook.domain.Section;
import com.pictet.adventurebook.domain.SectionType;
import com.pictet.adventurebook.exception.BookNotFoundException;
import com.pictet.adventurebook.exception.BookValidationException;
import com.pictet.adventurebook.repository.BookRepository;
import com.pictet.adventurebook.repository.GameSessionRepository;
import com.pictet.adventurebook.validation.BookValidator;
import com.pictet.adventurebook.web.dto.BookSummaryDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private GameSessionRepository gameSessionRepository;

    @Mock
    private BookValidator bookValidator;

    @InjectMocks
    private BookService bookService;

    private Book playableBook(String title) {
        return new Book(title, "Some Author", "EASY", List.of(
                new Section(1, "Start", SectionType.BEGIN, List.of(new Option("Go on", 2, null))),
                new Section(2, "The end", SectionType.END, List.of())
        ));
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchMapsRepositoryPageToSummaryDtos() {
        Book book = new Book("The Crystal Caverns", "Evelyn Stormrider", "EASY", List.of());
        Pageable pageable = PageRequest.of(0, 12);
        when(bookRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(book), pageable, 1));
        when(bookRepository.countSectionsByBookIds(any())).thenReturn(List.of());

        Page<BookSummaryDto> result = bookService.search(null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().title()).isEqualTo("The Crystal Caverns");
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchSkipsTheCountQueryWhenThePageIsEmpty() {
        Pageable pageable = PageRequest.of(0, 12);
        when(bookRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        bookService.search("nothing matches", null, pageable);

        verify(bookRepository, never()).countSectionsByBookIds(any());
    }

    @Test
    void loadPlayableBookReturnsTheBookWhenFound() {
        Book book = new Book("The Crystal Caverns", "Evelyn Stormrider", "EASY", List.of());
        when(bookRepository.findWithSectionsById(1L)).thenReturn(Optional.of(book));
        when(bookRepository.findSectionsWithOptionsByBookId(1L)).thenReturn(List.of());

        Book result = bookService.loadPlayableBook(1L);

        assertThat(result.getTitle()).isEqualTo("The Crystal Caverns");
    }

    @Test
    void loadPlayableBookThrowsWhenNotFound() {
        when(bookRepository.findWithSectionsById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.loadPlayableBook(99L))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void createSavesTheBookWhenItPassesValidation() {
        Book book = new Book("New Book", "Some Author", "EASY", List.of(
                new Section(1, "Start", SectionType.BEGIN, List.of())
        ));
        when(bookValidator.validate(book)).thenReturn(List.of());
        when(bookRepository.save(book)).thenReturn(book);

        Book result = bookService.create(book);

        assertThat(result).isSameAs(book);
        verify(bookRepository).save(book);
    }

    @Test
    void createRejectsABookThatFailsValidationWithoutSavingIt() {
        Book book = new Book("Broken Book", "Some Author", "EASY", List.of());
        when(bookValidator.validate(book)).thenReturn(List.of("Book has no beginning section"));

        assertThatThrownBy(() -> bookService.create(book))
                .isInstanceOf(BookValidationException.class)
                .satisfies(ex -> assertThat(((BookValidationException) ex).getErrors())
                        .containsExactly("Book has no beginning section"));
        verify(bookRepository, never()).save(any());
    }

    @Test
    void updateReplacesTheBooksContent() {
        Book existing = playableBook("Old Title");
        Book revision = new Book("New Title", "New Author", "HARD", List.of(
                new Section(1, "A rewritten start", SectionType.BEGIN, List.of(new Option("Onwards", 9, null))),
                new Section(9, "A rewritten end", SectionType.END, List.of())
        ));
        when(bookRepository.findWithSectionsById(1L)).thenReturn(Optional.of(existing));
        when(bookValidator.validate(revision)).thenReturn(List.of());
        when(bookRepository.save(existing)).thenReturn(existing);

        Book result = bookService.update(1L, revision);

        assertThat(result.getTitle()).isEqualTo("New Title");
        assertThat(result.getDifficulty()).isEqualTo("HARD");
        assertThat(result.getSections()).extracting(Section::getSectionNumber).containsExactly(1L, 9L);
    }

    @Test
    void updateValidatesTheRevisionAndRejectsABrokenOne() {
        Book existing = playableBook("Fine For Now");
        Book revision = new Book("Broken Revision", "Author", "EASY", List.of(
                new Section(1, "Nowhere to go", SectionType.NODE, List.of())
        ));
        when(bookRepository.findWithSectionsById(1L)).thenReturn(Optional.of(existing));
        when(bookValidator.validate(revision)).thenReturn(List.of("Book has no beginning section"));

        assertThatThrownBy(() -> bookService.update(1L, revision))
                .isInstanceOf(BookValidationException.class);
        verify(bookRepository, never()).save(any());
        // A rejected revision leaves the stored book untouched, not half-rewritten.
        assertThat(existing.getTitle()).isEqualTo("Fine For Now");
        assertThat(existing.getSections()).isNotEmpty();
    }

    // The reader's saved position is a section number in the version they started on. Once
    // the sections change, that number can point somewhere else entirely — so the game ends
    // rather than silently resuming into a rewritten story.
    @Test
    void updateEndsGamesStillInProgressOnTheBook() {
        Book existing = playableBook("Being Revised");
        GameSession playing = GameSession.start(existing, "test-player");
        GameSession finished = GameSession.start(existing, "test-player");
        finished.choose(0); // walks into the END section, so this one is FINISHED

        when(bookRepository.findWithSectionsById(1L)).thenReturn(Optional.of(existing));
        when(bookValidator.validate(any(Book.class))).thenReturn(List.of());
        when(bookRepository.save(existing)).thenReturn(existing);
        when(gameSessionRepository.findByBookId(1L)).thenReturn(List.of(playing, finished));

        bookService.update(1L, playableBook("Revised"));

        assertThat(playing.getStatus()).isEqualTo(GameStatus.ABANDONED);
        // A game that already ended is left alone — abandoning it would throw, and there is
        // no progress left to protect.
        assertThat(finished.getStatus()).isEqualTo(GameStatus.FINISHED);
    }

    @Test
    void updateThrowsWhenTheBookDoesNotExist() {
        when(bookRepository.findWithSectionsById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.update(99L, playableBook("Whatever")))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void deleteRemovesTheBookAndItsGames() {
        when(bookRepository.existsById(1L)).thenReturn(true);

        bookService.delete(1L);

        verify(gameSessionRepository).deleteByBookId(1L);
        verify(bookRepository).deleteById(1L);
    }

    @Test
    void deleteThrowsWhenTheBookDoesNotExist() {
        when(bookRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> bookService.delete(99L))
                .isInstanceOf(BookNotFoundException.class);
        verify(bookRepository, never()).deleteById(any());
    }

    @Test
    void countGamesInProgressAsksForPlayingSessionsOnly() {
        when(gameSessionRepository.countByBookIdAndStatus(1L, GameStatus.PLAYING)).thenReturn(2L);

        assertThat(bookService.countGamesInProgress(1L)).isEqualTo(2L);
    }
}
