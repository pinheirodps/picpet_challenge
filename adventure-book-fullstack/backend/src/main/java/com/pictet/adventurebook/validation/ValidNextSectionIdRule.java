package com.pictet.adventurebook.validation;

import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.Section;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Every option's {@code gotoId} must point at a section that actually exists in the same
 * book — otherwise the reader can pick a choice that leads nowhere.
 */
@Component
public final class ValidNextSectionIdRule implements ValidationRule {

    @Override
    public List<String> check(Book book) {
        return book.getSections().stream()
                .flatMap(section -> section.getOptions().stream()
                        .filter(option -> book.findBySectionNumber(option.getGotoId()).isEmpty())
                        .map(option -> "Section " + section.getSectionNumber()
                                + " has an option pointing to non-existent section " + option.getGotoId()))
                .toList();
    }
}
