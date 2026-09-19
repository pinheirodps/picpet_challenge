package com.pictet.adventurebook.web.dto;

import com.pictet.adventurebook.domain.SectionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * One numbered section being submitted when creating a book (Objective 5). {@code options}
 * is allowed to be empty here even for a non-ending section — that's exactly the kind of
 * mistake {@link com.pictet.adventurebook.validation.BookValidator} exists to catch, with a
 * clear error, rather than rejecting the request before validation gets a chance to run.
 */
public record SectionRequest(
        @NotNull @Positive Long id,
        @NotBlank String text,
        @NotNull SectionType type,
        @Valid List<OptionRequest> options
) {
}
