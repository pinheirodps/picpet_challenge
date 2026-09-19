package com.pictet.adventurebook.repository;

import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.GameSession;
import com.pictet.adventurebook.domain.GameStatus;
import com.pictet.adventurebook.domain.Option;
import com.pictet.adventurebook.domain.Section;
import com.pictet.adventurebook.domain.SectionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the "resume a saved game" query (Objective 4): only sessions still {@link
 * GameStatus#PLAYING} come back, and the most recently updated one comes first.
 */
@DataJpaTest
class GameSessionRepositoryIntegrationTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Test
    void onlyReturnsSessionsStillInProgress() {
        Book book = bookRepository.save(sampleBook());

        GameSession playing = GameSession.start(book, "test-player");
        GameSession finished = GameSession.start(book, "test-player");
        finished.choose(0);

        gameSessionRepository.save(playing);
        gameSessionRepository.save(finished);

        List<GameSession> saved = gameSessionRepository.findResumableFor("test-player", GameStatus.PLAYING);

        assertThat(saved).extracting(GameSession::getId).containsExactly(playing.getId());
    }

    // The point of the playerId column: one reader's resume list must not show another's
    // games, which is what happened before it existed.
    @Test
    void onlyReturnsThisPlayersSessions() {
        Book book = bookRepository.save(sampleBook());

        GameSession mine = gameSessionRepository.save(GameSession.start(book, "reader-a"));
        gameSessionRepository.save(GameSession.start(book, "reader-b"));

        List<GameSession> saved = gameSessionRepository.findResumableFor("reader-a", GameStatus.PLAYING);

        assertThat(saved).extracting(GameSession::getId).containsExactly(mine.getId());
    }

    @Test
    void findsThisPlayersGameOnABookButNotSomebodyElses() {
        Book book = bookRepository.save(sampleBook());
        gameSessionRepository.save(GameSession.start(book, "reader-b"));

        assertThat(gameSessionRepository.findResumableOnBook(book.getId(), "reader-a", GameStatus.PLAYING))
                .isEmpty();

        GameSession mine = gameSessionRepository.save(GameSession.start(book, "reader-a"));

        assertThat(gameSessionRepository.findResumableOnBook(book.getId(), "reader-a", GameStatus.PLAYING))
                .map(GameSession::getId)
                .contains(mine.getId());
    }

    private Book sampleBook() {
        Section begin = new Section(1, "Start", SectionType.BEGIN, List.of(new Option("Go to 2", 2, null)));
        Section end = new Section(2, "End", SectionType.END, List.of());
        return new Book("Test Book", "Test Author", "EASY", List.of(begin, end));
    }
}
