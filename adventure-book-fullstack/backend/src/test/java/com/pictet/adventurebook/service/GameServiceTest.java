package com.pictet.adventurebook.service;

import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.GameSession;
import com.pictet.adventurebook.domain.GameStatus;
import com.pictet.adventurebook.domain.Option;
import com.pictet.adventurebook.domain.Section;
import com.pictet.adventurebook.domain.SectionType;
import com.pictet.adventurebook.exception.GameSessionNotFoundException;
import com.pictet.adventurebook.repository.GameSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private GameSessionRepository gameSessionRepository;

    @Mock
    private BookService bookService;

    private GameService gameService;

    private Book book;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        gameService = new GameService(gameSessionRepository, bookService);
        book = new Book("Test Book", "Test Author", "EASY", List.of(
                new Section(1, "Start", SectionType.BEGIN, List.of(new Option("Go to 2", 2, null))),
                new Section(2, "End", SectionType.END, List.of())
        ));
    }

    @Test
    void startGameLoadsTheBookAndSavesANewSession() {
        when(gameSessionRepository.findResumableOnBook(1L, "test-player", GameStatus.PLAYING))
                .thenReturn(Optional.empty());
        when(bookService.loadPlayableBook(1L)).thenReturn(book);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameSession session = gameService.startGame(1L, "test-player");

        assertThat(session.getCurrentSectionNumber()).isEqualTo(1);
        verify(gameSessionRepository).save(any(GameSession.class));
    }

    // Pressing "begin" on a book that's already part-way through is almost always a misclick.
    // Creating a second session there leaves two entries in the saved-games list that look
    // identical, and the reader loses track of which is which.
    @Test
    void startGameResumesAnExistingGameInsteadOfCreatingASecond() {
        GameSession existing = GameSession.start(book, "test-player");
        existing.choose(0);
        when(gameSessionRepository.findResumableOnBook(1L, "test-player", GameStatus.PLAYING))
                .thenReturn(Optional.of(existing));

        GameSession session = gameService.startGame(1L, "test-player");

        assertThat(session).isSameAs(existing);
        assertThat(session.getCurrentSectionNumber()).isEqualTo(2);
        verify(gameSessionRepository, never()).save(any(GameSession.class));
        verify(bookService, never()).loadPlayableBook(any());
    }

    @Test
    void startGameAfterTheEarlierOneEndedCreatesAFreshSession() {
        // Only PLAYING sessions are resumable, so a finished or stopped game doesn't block a
        // new one — that's how a reader deliberately plays a book again.
        when(gameSessionRepository.findResumableOnBook(1L, "test-player", GameStatus.PLAYING))
                .thenReturn(Optional.empty());
        when(bookService.loadPlayableBook(1L)).thenReturn(book);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameSession session = gameService.startGame(1L, "test-player");

        assertThat(session.getCurrentSectionNumber()).isEqualTo(1);
        assertThat(session.getHealth()).isEqualTo(GameSession.STARTING_HEALTH);
    }

    @Test
    void chooseLoadsTheSessionAppliesTheChoiceAndSavesIt() {
        GameSession session = GameSession.start(book, "test-player");
        when(gameSessionRepository.findWithBookAndSectionsById(42L)).thenReturn(Optional.of(session));
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameSession result = gameService.choose(42L, 0);

        assertThat(result.getCurrentSectionNumber()).isEqualTo(2);
        ArgumentCaptor<GameSession> captor = ArgumentCaptor.forClass(GameSession.class);
        verify(gameSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentSectionNumber()).isEqualTo(2);
    }

    @Test
    void chooseThrowsWhenTheSessionDoesNotExist() {
        when(gameSessionRepository.findWithBookAndSectionsById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.choose(99L, 0))
                .isInstanceOf(GameSessionNotFoundException.class);
    }

    @Test
    void listSavedGamesReturnsOnlyInProgressSessionsMostRecentFirst() {
        GameSession session = GameSession.start(book, "test-player");
        when(gameSessionRepository.findResumableFor("test-player", GameStatus.PLAYING))
                .thenReturn(List.of(session));

        List<GameSession> result = gameService.listSavedGames("test-player");

        assertThat(result).containsExactly(session);
    }
}
