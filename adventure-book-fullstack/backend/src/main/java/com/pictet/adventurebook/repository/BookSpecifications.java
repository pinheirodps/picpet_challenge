package com.pictet.adventurebook.repository;

import com.pictet.adventurebook.domain.Book;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Building blocks for the library's search and filter (Objective 1). Kept as small,
 * composable {@link Specification}s instead of one big query method, so the two filters
 * can be combined or used on their own without duplicating query logic.
 */
public final class BookSpecifications {

    private BookSpecifications() {
    }

    /**
     * Matches books whose title or author contains {@code text}, case-insensitively.
     * A blank or {@code null} search text matches everything.
     */
    public static Specification<Book> titleOrAuthorContains(String text) {
        if (!StringUtils.hasText(text)) {
            return (root, query, cb) -> cb.conjunction();
        }
        String pattern = "%" + text.toLowerCase() + "%";
        return (root, query, cb) -> {
            Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
            Predicate authorMatch = cb.like(cb.lower(root.get("author")), pattern);
            return cb.or(titleMatch, authorMatch);
        };
    }

    /**
     * Matches books with the given difficulty, exactly. A blank or {@code null} value
     * matches everything.
     */
    public static Specification<Book> hasDifficulty(String difficulty) {
        if (!StringUtils.hasText(difficulty)) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.equal(cb.lower(root.get("difficulty")), difficulty.toLowerCase());
    }
}
