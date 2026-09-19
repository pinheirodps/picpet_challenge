package com.pictet.adventurebook.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Request body for picking one of the current section's options, by its index. */
public record ChooseOptionRequest(@NotNull @PositiveOrZero Integer optionIndex) {
}
