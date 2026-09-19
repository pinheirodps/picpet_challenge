package com.pictet.adventurebook.web.dto;

import jakarta.validation.constraints.NotNull;

/** Request body to start a new game on a given book. */
public record StartGameRequest(@NotNull Long bookId) {
}
