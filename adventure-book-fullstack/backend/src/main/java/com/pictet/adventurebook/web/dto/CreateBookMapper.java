package com.pictet.adventurebook.web.dto;

import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.Consequence;
import com.pictet.adventurebook.domain.Option;
import com.pictet.adventurebook.domain.Section;

import java.util.List;

/**
 * Converts a submitted {@link CreateBookRequest} into the same domain objects the seed
 * loader builds from JSON. Kept deliberately dumb — no validation here, just shape
 * conversion — so {@link com.pictet.adventurebook.validation.BookValidator} stays the one
 * place that decides what makes a book valid, whether it came from a seed file or a
 * request body.
 */
public final class CreateBookMapper {

    private CreateBookMapper() {
    }

    public static Book toBook(CreateBookRequest request) {
        return new Book(
                request.title(),
                request.author(),
                request.difficulty(),
                request.sections().stream().map(CreateBookMapper::toSection).toList()
        );
    }

    private static Section toSection(SectionRequest request) {
        List<Option> options = request.options() == null
                ? List.of()
                : request.options().stream().map(CreateBookMapper::toOption).toList();
        return new Section(request.id(), request.text(), request.type(), options);
    }

    private static Option toOption(OptionRequest request) {
        Consequence consequence = request.consequence() == null ? null : toConsequence(request.consequence());
        return new Option(request.description(), request.gotoId(), consequence);
    }

    private static Consequence toConsequence(ConsequenceRequest request) {
        return new Consequence(request.type(), request.value(), request.text());
    }
}
