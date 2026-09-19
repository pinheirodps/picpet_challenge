package com.pictet.adventurebook.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** A new book submitted for the library (Objective 5, extra). */
public record CreateBookRequest(
        @NotBlank String title,
        String author,
        String difficulty,
        @NotEmpty @Valid List<SectionRequest> sections
) {
}
