package com.pictet.adventurebook.web.dto;

import com.pictet.adventurebook.domain.ConsequenceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** The health effect of picking an option, as submitted when creating a book (Objective 5). */
public record ConsequenceRequest(
        @NotNull ConsequenceType type,
        @Positive int value,
        String text
) {
}
