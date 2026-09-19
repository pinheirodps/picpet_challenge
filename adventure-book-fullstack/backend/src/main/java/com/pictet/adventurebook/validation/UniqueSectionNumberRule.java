package com.pictet.adventurebook.validation;

import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.Section;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Every section number must be unique within a book. Without this, {@link
 * Book#findBySectionNumber} has no way to tell which of two same-numbered sections a
 * {@code gotoId} was meant to reach, and silently picks whichever one happens to come
 * first — this rule turns that ambiguity into a validation error at load time instead.
 */
@Component
public final class UniqueSectionNumberRule implements ValidationRule {

    @Override
    public List<String> check(Book book) {
        Map<Long, Long> countBySectionNumber = book.getSections().stream()
                .collect(Collectors.groupingBy(Section::getSectionNumber, Collectors.counting()));

        return countBySectionNumber.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(entry -> "Section number " + entry.getKey() + " is used by " + entry.getValue() + " sections")
                .toList();
    }
}
