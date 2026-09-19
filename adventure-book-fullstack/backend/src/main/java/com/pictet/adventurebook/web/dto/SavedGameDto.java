package com.pictet.adventurebook.web.dto;

import java.time.Instant;

/**
 * One entry in the "resume a saved game" list (Objective 4): enough to show the player
 * what they were playing and when, without loading the full section/option graph — that
 * only gets loaded once they pick a game to resume, via {@code GET /api/games/{id}}.
 */
public record SavedGameDto(Long gameId, String bookTitle, int health, Instant updatedAt) {
}
