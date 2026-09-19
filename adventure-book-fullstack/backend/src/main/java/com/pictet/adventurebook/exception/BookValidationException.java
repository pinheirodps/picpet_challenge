package com.pictet.adventurebook.exception;

import java.util.List;

/**
 * Thrown when a submitted book fails {@link com.pictet.adventurebook.validation.BookValidator}.
 * Carries every reason it failed, not just the first, so the submitter can fix everything
 * in one pass instead of discovering problems one at a time.
 */
public class BookValidationException extends RuntimeException {

    private final List<String> errors;

    public BookValidationException(List<String> errors) {
        super("Book is invalid: " + errors);
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
