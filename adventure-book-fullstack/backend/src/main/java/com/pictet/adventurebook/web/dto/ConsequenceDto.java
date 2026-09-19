package com.pictet.adventurebook.web.dto;

import com.pictet.adventurebook.domain.ConsequenceType;

/**
 * What just happened to the player as a result of their last choice, so the UI can explain
 * a health change instead of silently showing a smaller number.
 *
 * @param healthChange the signed delta actually applied — negative for damage, positive for
 *                     healing — so the client doesn't have to re-derive it from the type
 * @param text         the book's own flavour text for this consequence, which may be null
 */
public record ConsequenceDto(ConsequenceType type, int healthChange, String text) {
}
