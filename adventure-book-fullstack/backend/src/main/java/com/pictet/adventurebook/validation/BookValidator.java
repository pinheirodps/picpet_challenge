package com.pictet.adventurebook.validation;

import com.pictet.adventurebook.domain.Book;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs every {@link ValidationRule} against a book and collects all the reasons it fails,
 * instead of stopping at the first one — useful for showing complete feedback to whoever
 * is submitting a new book.
 *
 * <p>Spring injects every {@code @Component} that implements {@link ValidationRule}, so
 * adding a new rule to the {@code validation} package is enough to have it enforced here
 * too — nothing in this class needs to change.
 */
@Component
public class BookValidator {

    private final List<ValidationRule> rules;

    public BookValidator(List<ValidationRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public List<String> validate(Book book) {
        List<String> errors = new ArrayList<>();
        for (ValidationRule rule : rules) {
            errors.addAll(rule.check(book));
        }
        return errors;
    }

    public boolean isValid(Book book) {
        return validate(book).isEmpty();
    }
}
