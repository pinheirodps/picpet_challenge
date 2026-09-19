package com.pictet.adventurebook.repository;

import com.pictet.adventurebook.domain.GameSession;
import com.pictet.adventurebook.domain.GameStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link GameSession}.
 *
 * <ul>
 *   <li>{@link #findWithBookAndSectionsById} loads a session with its book and the book's
 *       sections in one query — the session's own state (current section, health, status)
 *       never needs an option's details, only {@link
 *       com.pictet.adventurebook.service.BookService#loadPlayableBook} does, which fetches
 *       options separately. Fetching {@code sections} and {@code options} as two parallel
 *       collection joins in a single query would produce a cross product (see {@link
 *       com.pictet.adventurebook.repository.BookRepository}'s javadoc for why), so this
 *       repository never does that.</li>
 *   <li>{@link #findByStatusOrderByUpdatedAtDesc} backs the "resume a saved game" list
 *       (Objective 4) and only needs the book's title, so it fetch-joins just {@code book},
 *       not {@code sections} — the lightest query that still avoids N+1 on {@code
 *       session.getBook().getTitle()} for every row.</li>
 * </ul>
 */
public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    @Query("select distinct gs from GameSession gs "
            + "join fetch gs.book b left join fetch b.sections "
            + "where gs.id = :id")
    Optional<GameSession> findWithBookAndSectionsById(@Param("id") Long id);

    @Query("select gs from GameSession gs join fetch gs.book where gs.status = :status order by gs.updatedAt desc")
    List<GameSession> findByStatusOrderByUpdatedAtDesc(@Param("status") GameStatus status);

    /**
     * How many games are still in progress on a book. Used to warn the reader before they
     * edit or delete it, so "2 games in progress will be lost" is a real number rather than
     * a guess.
     */
    long countByBookIdAndStatus(Long bookId, GameStatus status);

    /**
     * The most recent game still in progress on a book, if there is one — what "start a game"
     * resumes instead of creating a second session on the same book.
     *
     * <p>Fetches the book and its sections, because the caller goes straight on to read the
     * session's current section. Options are loaded separately, for the cross-product reason
     * this repository's class javadoc explains.
     */
    @Query("select gs from GameSession gs "
            + "join fetch gs.book b left join fetch b.sections "
            + "where b.id = :bookId and gs.status = :status "
            + "order by gs.updatedAt desc limit 1")
    Optional<GameSession> findLatestByBookIdAndStatus(@Param("bookId") Long bookId,
                                                      @Param("status") GameStatus status);

    /**
     * Every session belonging to a book, whatever its status — the sessions that have to be
     * dealt with before the book itself can go.
     */
    List<GameSession> findByBookId(Long bookId);

    /**
     * Removes every session of a book in one statement, so deleting a book doesn't turn into
     * one delete per saved game.
     *
     * <p>This is a bulk operation: it bypasses the persistence context, so any session loaded
     * in the same transaction would still be sitting there stale. Nothing here reads a
     * session after calling it, and the transaction ends immediately afterwards.
     */
    @Modifying
    @Query("delete from GameSession gs where gs.book.id = :bookId")
    void deleteByBookId(@Param("bookId") Long bookId);
}
