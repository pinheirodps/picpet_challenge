package com.pictet.adventurebook.service;

import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.GameSession;
import com.pictet.adventurebook.domain.GameStatus;
import com.pictet.adventurebook.exception.GameSessionNotFoundException;
import com.pictet.adventurebook.repository.GameSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Starting a game, playing through it one choice at a time (Objectives 2 and 3), resuming
 * a game that's still in progress and stopping one for good (Objective 4).
 *
 * <p>All the actual game rules — moving between sections, applying consequences, detecting
 * death or an ending — live on {@link GameSession} itself; this service is just the
 * transactional boundary that loads a session, asks it to act, and saves the result.
 * There's no separate "save" operation: every {@link #choose} call already persists the
 * session via JPA, so a saved game is simply one that's still {@link GameStatus#PLAYING}.
 */
@Service
@Transactional
public class GameService {

    private final GameSessionRepository gameSessionRepository;
    private final BookService bookService;

    public GameService(GameSessionRepository gameSessionRepository, BookService bookService) {
        this.gameSessionRepository = gameSessionRepository;
        this.bookService = bookService;
    }

    /**
     * Starts a game on a book — or hands back the one already in progress on it.
     *
     * <p>Starting a second game on a book the reader is part-way through is almost always an
     * accident: they press "begin" again instead of resuming, and end up with two sessions
     * that look identical in the saved-games list (same title, often the same health) with no
     * way to tell which is which. Resuming is what they meant, and it can't lose progress.
     *
     * <p>Deliberately playing a book twice at once is still possible — stop the first game and
     * start again. That's the rarer intent, so it's the one that takes the extra step.
     */
    public GameSession startGame(Long bookId, String playerId) {
        return gameSessionRepository.findResumableOnBook(bookId, playerId, GameStatus.PLAYING)
                .map(this::withOptionsLoaded)
                .orElseGet(() -> {
                    Book book = bookService.loadPlayableBook(bookId);
                    return gameSessionRepository.save(GameSession.start(book, playerId));
                });
    }

    public GameSession choose(Long gameId, int optionIndex) {
        GameSession session = loadSession(gameId);
        session.choose(optionIndex);
        return gameSessionRepository.save(session);
    }

    /** Stops a game from the header's stop control — see {@link GameSession#abandon()}. */
    public GameSession abandon(Long gameId) {
        GameSession session = loadSession(gameId);
        session.abandon();
        return gameSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public GameSession getSession(Long gameId) {
        return loadSession(gameId);
    }

    /**
     * This reader's in-progress games, most recently played first, for the "resume" list.
     *
     * <p>Scoped by {@code playerId} so one reader's list doesn't show — and let them take
     * over — another's game. See {@link GameSession#getPlayerId()} for what that id is and,
     * importantly, what it isn't.
     */
    @Transactional(readOnly = true)
    public List<GameSession> listSavedGames(String playerId) {
        return gameSessionRepository.findResumableFor(playerId, GameStatus.PLAYING);
    }

    /**
     * Shared loader for the read and write paths. Deliberately not annotated: calling an
     * annotated method from inside this class bypasses the proxy, so a {@code readOnly}
     * annotation here would be silently ignored by {@link #choose} — and would break it if
     * a future refactor ever routed the call through the proxy.
     */
    private GameSession loadSession(Long gameId) {
        GameSession session = gameSessionRepository.findWithBookAndSectionsById(gameId)
                .orElseThrow(() -> new GameSessionNotFoundException(gameId));
        return withOptionsLoaded(session);
    }

    /**
     * Fills in the options on an already-loaded session's book. The sections come with the
     * session; the options need their own query, which Hibernate merges into the sections
     * already in the persistence context.
     */
    private GameSession withOptionsLoaded(GameSession session) {
        bookService.loadOptionsInto(session.getBook().getId());
        return session;
    }
}
