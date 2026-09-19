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

        GameSession playing = GameSession.start(book);
        GameSession won = GameSession.start(book);
        won.choose(0);

        gameSessionRepository.save(playing);
        gameSessionRepository.save(won);

        List<GameSession> saved = gameSessionRepository.findByStatusOrderByUpdatedAtDesc(GameStatus.PLAYING);

        assertThat(saved).extracting(GameSession::getId).containsExactly(playing.getId());
    }

    private Book sampleBook() {
        Section begin = new Section(1, "Start", SectionType.BEGIN, List.of(new Option("Go to 2", 2, null)));
        Section end = new Section(2, "End", SectionType.END, List.of());
        return new Book("Test Book", "Test Author", "EASY", List.of(begin, end));
    }
}
