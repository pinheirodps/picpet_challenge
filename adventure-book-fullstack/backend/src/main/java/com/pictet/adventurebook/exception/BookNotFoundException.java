package com.pictet.adventurebook.exception;

/**
 * Thrown when a book id doesn't exist. Translated to a 404 response by
 * {@link GlobalExceptionHandler}.
 */
public class BookNotFoundException extends RuntimeException {

    public BookNotFoundException(Long bookId) {
        super("No book found with id " + bookId);
    }
}
