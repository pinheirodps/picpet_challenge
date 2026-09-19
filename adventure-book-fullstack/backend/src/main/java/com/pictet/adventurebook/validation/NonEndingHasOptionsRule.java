package com.pictet.adventurebook.validation;

import com.pictet.adventurebook.domain.Book;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A section that isn't an ending must offer at least one option — otherwise the reader
 * reaches a dead end the book never intended as one.
 */
@Component
public final class NonEndingHasOptionsRule implements ValidationRule {

    @Override
    public List<String> check(Book book) {
        return book.getSections().stream()
                .filter(section -> !section.isEnding() && section.getOptions().isEmpty())
                .map(section -> "Section " + section.getSectionNumber() + " is not an ending but has no options")
                .toList();
    }
}
