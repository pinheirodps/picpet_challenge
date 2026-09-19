package com.pictet.adventurebook.web.dto;

import com.pictet.adventurebook.domain.GameStatus;

import java.util.List;

/**
 * The full state the frontend needs to render one turn of the game: the header info
 * (book name, health), the current section's text, the choices available — empty once
 * {@code status} is no longer {@code PLAYING} — and what the last choice did to the
 * player, if anything.
 *
 * @param lastConsequence null on the first turn and after any choice that carried no
 *                        consequence; otherwise what changed the player's health and why
 */
public record GameSessionDto(
        Long gameId,
        String bookTitle,
        int health,
        int maxHealth,
        GameStatus status,
        String sectionText,
        List<OptionDto> options,
        ConsequenceDto lastConsequence
) {
}
