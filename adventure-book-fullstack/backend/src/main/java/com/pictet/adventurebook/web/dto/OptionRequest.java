package com.pictet.adventurebook.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** One choice on a section being submitted when creating a book (Objective 5). */
public record OptionRequest(
        @NotBlank String description,
        @NotNull @Positive Long gotoId,
        @Valid ConsequenceRequest consequence
) {
}
