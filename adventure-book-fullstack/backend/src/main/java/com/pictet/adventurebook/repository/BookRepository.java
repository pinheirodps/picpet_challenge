package com.pictet.adventurebook.repository;

import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link Book}. Two different loading strategies live here on purpose:
 *
 * <ul>
 *   <li>{@link #findAll} (inherited, used with a {@code Specification} for search/filter)
 *       only needs title/author/difficulty for the library listing, so it never touches
 *       the section/option tables — no N+1 risk because nothing nested is fetched.</li>
 *   <li>{@link #findWithSectionsById} and {@link #findSectionsWithOptionsByBookId} load a
 *       single book's sections and options in two queries instead of one. Fetching both
 *       {@code sections} and {@code options} as parallel collection joins in a single query
 *       produces a cross product — every section row repeats once per option — and a
 *       {@code distinct} on the root entity does not undo that duplication inside the
 *       {@code sections} collection itself, so the book ends up with the same section
 *       several times over. Two queries against the same persistence context avoid this:
 *       Hibernate merges the second query's results into the {@link Book} already loaded
 *       by the first, so {@link Book#getSections()} ends up complete and duplicate-free.</li>
 * </ul>
 */
public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    @Query("select distinct b from Book b left join fetch b.sections where b.id = :id")
    Optional<Book> findWithSectionsById(@Param("id") Long id);

    @Query("select distinct s from Section s left join fetch s.options where s.book.id = :bookId")
    List<Section> findSectionsWithOptionsByBookId(@Param("bookId") Long bookId);

    /**
     * Section counts for a batch of books, as {@code [bookId, count]} rows. The listing
     * shows a chapter count per card, and doing that through {@code book.getSections()}
     * would lazily load every section of every row on the page — the N+1 this repository
     * is otherwise careful to avoid. One grouped query covers the whole page instead.
     */
    @Query("select s.book.id, count(s) from Section s where s.book.id in :bookIds group by s.book.id")
    List<Object[]> countSectionsByBookIds(@Param("bookIds") Collection<Long> bookIds);
}
