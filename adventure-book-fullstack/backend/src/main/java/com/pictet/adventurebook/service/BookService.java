package com.pictet.adventurebook.service;

import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.GameSession;
import com.pictet.adventurebook.domain.GameStatus;
import com.pictet.adventurebook.exception.BookNotFoundException;
import com.pictet.adventurebook.exception.BookValidationException;
import com.pictet.adventurebook.repository.BookRepository;
import com.pictet.adventurebook.repository.BookSpecifications;
import com.pictet.adventurebook.repository.GameSessionRepository;
import com.pictet.adventurebook.validation.BookValidator;
import com.pictet.adventurebook.web.dto.BookSummaryDto;
import com.pictet.adventurebook.web.dto.BookSummaryMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read access to books for the library (Objective 1), for starting/resuming a game, and
 * creating new books (Objective 5).
 *
 * <p>{@link #search} and {@link #loadPlayableBook} deliberately use different repository
 * queries — the listing never needs a book's sections, and loading them there would be
 * wasted work multiplied by every row on the page. See {@link BookRepository} for the
 * fetch strategy behind each one.
 */
@Service
@Transactional(readOnly = true)
public class BookService {

    private final BookRepository bookRepository;
    private final GameSessionRepository gameSessionRepository;
    private final BookValidator bookValidator;

    public BookService(BookRepository bookRepository, GameSessionRepository gameSessionRepository,
                       BookValidator bookValidator) {
        this.bookRepository = bookRepository;
        this.gameSessionRepository = gameSessionRepository;
        this.bookValidator = bookValidator;
    }

    /**
     * Lists books for the library home page, optionally narrowed by free-text search
     * (matched against title and author) and by exact difficulty.
     *
     * <p>Section counts come from one grouped query over the page's book ids rather than
     * from {@code book.getSections()}, which would lazily load every section of every row.
     */
    public Page<BookSummaryDto> search(String query, String difficulty, Pageable pageable) {
        Specification<Book> spec = Specification
                .where(BookSpecifications.titleOrAuthorContains(query))
                .and(BookSpecifications.hasDifficulty(difficulty));

        Page<Book> books = bookRepository.findAll(spec, pageable);
        Map<Long, Integer> sectionCounts = countSectionsFor(books.getContent());

        return books.map(book -> BookSummaryMapper.toSummary(book, sectionCounts.getOrDefault(book.getId(), 0)));
    }

    /**
     * Loads a book with every section and option, ready to play.
     *
     * <p>Deliberately not cached: the returned graph is a managed entity with lazy
     * collections, and handing a detached copy of it to a later transaction (with
     * {@code open-in-view} disabled) is how {@code LazyInitializationException} happens in
     * production. It's only called once per game — when the game starts — so caching would
     * buy nothing for that risk.
     *
     * @throws BookNotFoundException if no book has that id
     */
    public Book loadPlayableBook(Long bookId) {
        Book book = bookRepository.findWithSectionsById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        loadOptionsInto(bookId);
        return book;
    }

    /**
     * Populates the {@code options} collection on every section of an already-loaded book,
     * by running a second query in the same persistence context — Hibernate merges its
     * results into the sections already loaded. See {@link BookRepository} for why this
     * isn't a single query with two fetch joins.
     *
     * <p>Callers rely on the side effect rather than the return value, so this is only safe
     * to call while a transaction (and therefore a persistence context) is open — which
     * both {@link #loadPlayableBook} and {@link GameService#getSession} guarantee.
     */
    public void loadOptionsInto(Long bookId) {
        bookRepository.findSectionsWithOptionsByBookId(bookId);
    }

    /**
     * Validates a newly submitted book with the same rules the seed loader enforces, and
     * persists it if it passes. There's no separate validation path for user-submitted
     * books — reusing {@link BookValidator} means a book is either playable or rejected
     * with a clear reason, regardless of where it came from.
     *
     * @throws BookValidationException if the book fails any validation rule
     */
    @Transactional
    public Book create(Book book) {
        List<String> errors = bookValidator.validate(book);
        if (!errors.isEmpty()) {
            throw new BookValidationException(errors);
        }
        return bookRepository.save(book);
    }

    /**
     * Replaces a book's content with a revised version, validated by the same rules as a new
     * one — a book that's already in the library is held to the same standard as one being
     * added, so editing can't sneak an unplayable book past the rules.
     *
     * <p>Any game still in progress on this book is ended. The reader's saved position is a
     * section number in the <em>old</em> map; once the sections change, that number may point
     * somewhere else or nowhere at all, and silently resuming into a rewritten story is worse
     * than being told the book changed. Callers should warn before reaching this point —
     * {@link #countGamesInProgress} exists for that.
     *
     * @throws BookNotFoundException if no book has that id
     * @throws BookValidationException if the revised book fails any validation rule
     */
    @Transactional
    public Book update(Long bookId, Book revision) {
        Book existing = bookRepository.findWithSectionsById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));

        // Validate the revision before touching what's stored, so a rejected edit leaves the
        // book exactly as it was.
        List<String> errors = bookValidator.validate(revision);
        if (!errors.isEmpty()) {
            throw new BookValidationException(errors);
        }

        // Two steps with a flush between them. Most revisions reuse the same section numbers,
        // and (book_id, section_number) is unique — without the flush Hibernate issues the
        // inserts before the deletes and the constraint fires.
        existing.reviseMetadata(revision.getTitle(), revision.getAuthor(), revision.getDifficulty());
        bookRepository.flush();

        existing.addSections(revision.getSections());
        endGamesOn(bookId);
        return bookRepository.save(existing);
    }

    /**
     * Removes a book from the library, along with every game played on it — a session can't
     * outlive the book it reads from, since its saved position means nothing without those
     * sections.
     *
     * @throws BookNotFoundException if no book has that id
     */
    @Transactional
    public void delete(Long bookId) {
        if (!bookRepository.existsById(bookId)) {
            throw new BookNotFoundException(bookId);
        }
        gameSessionRepository.deleteByBookId(bookId);
        bookRepository.deleteById(bookId);
    }

    /**
     * How many games are still in progress on a book, so the UI can warn about what an edit
     * or a delete is about to end.
     */
    public long countGamesInProgress(Long bookId) {
        return gameSessionRepository.countByBookIdAndStatus(bookId, GameStatus.PLAYING);
    }

    /**
     * Ends every in-progress game on a book, as {@link GameStatus#ABANDONED} — the same state
     * a reader who stops a game reaches, since from their side the outcome is identical: the
     * game keeps its history but is no longer resumable.
     */
    private void endGamesOn(Long bookId) {
        gameSessionRepository.findByBookId(bookId).stream()
                .filter(session -> session.getStatus() == GameStatus.PLAYING)
                .forEach(GameSession::abandon);
    }

    private Map<Long, Integer> countSectionsFor(List<Book> books) {
        if (books.isEmpty()) {
            return Map.of();
        }

        List<Long> bookIds = books.stream().map(Book::getId).toList();
        return bookRepository.countSectionsByBookIds(bookIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).intValue(),
                        (first, second) -> first));
    }
}
