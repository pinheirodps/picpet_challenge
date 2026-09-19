package com.pictet.adventurebook.validation;

import com.pictet.adventurebook.domain.Book;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A book needs at least one END section — without one, the story can never finish.
 * Having several endings is fine.
 */
@Component
public final class HasEndingRule implements ValidationRule {

    @Override
    public List<String> check(Book book) {
        if (book.endings().isEmpty()) {
            return List.of("Book has no ending section");
        }
        return List.of();
    }
}
