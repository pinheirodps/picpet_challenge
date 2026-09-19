package com.pictet.adventurebook.web.dto;

import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.Consequence;
import com.pictet.adventurebook.domain.Option;
import com.pictet.adventurebook.domain.Section;

import java.util.List;

/**
 * Turns a fully loaded {@link Book} into the shape the editor reads. The inverse direction
 * is {@link CreateBookMapper}, which the same request body feeds on the way back in.
 *
 * <p>Sections and options must already be loaded — this walks both collections, so calling
 * it on a book fetched without them would trigger a lazy load per section, or fail outright
 * once the transaction is closed. {@link
 * com.pictet.adventurebook.service.BookService#loadPlayableBook} is the method that fetches
 * a book in the right state for this.
 */
public final class BookDetailMapper {

    private BookDetailMapper() {
    }

    public static BookDetailDto toDetail(Book book, long gamesInProgress) {
        return new BookDetailDto(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getDifficulty(),
                gamesInProgress,
                book.getSections().stream().map(BookDetailMapper::toSection).toList()
        );
    }

    private static BookDetailDto.SectionDetail toSection(Section section) {
        return new BookDetailDto.SectionDetail(
                section.getSectionNumber(),
                section.getText(),
                section.getType(),
                section.getOptions().stream().map(BookDetailMapper::toOption).toList()
        );
    }

    private static BookDetailDto.OptionDetail toOption(Option option) {
        return new BookDetailDto.OptionDetail(
                option.getDescription(),
                option.getGotoId(),
                option.hasConsequence() ? toConsequence(option.getConsequence()) : null
        );
    }

    private static BookDetailDto.ConsequenceDetail toConsequence(Consequence consequence) {
        return new BookDetailDto.ConsequenceDetail(consequence.type(), consequence.value(), consequence.text());
    }
}
