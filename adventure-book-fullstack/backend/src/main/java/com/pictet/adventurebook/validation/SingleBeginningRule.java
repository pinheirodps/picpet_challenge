package com.pictet.adventurebook.validation;

import com.pictet.adventurebook.domain.Book;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A book needs exactly one BEGIN section — none or more than one both make it impossible
 * to know where the reader should start.
 */
@Component
public final class SingleBeginningRule implements ValidationRule {

    @Override
    public List<String> check(Book book) {
        int count = book.beginnings().size();
        if (count == 0) {
            return List.of("Book has no beginning section");
        }
        if (count > 1) {
            return List.of("Book has more than one beginning section (" + count + " found)");
        }
        return List.of();
    }
}
