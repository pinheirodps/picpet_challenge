package com.pictet.adventurebook.web.dto;

/**
 * One choice shown to the player. {@code index} is the position to send back in
 * {@link ChooseOptionRequest} — the frontend never needs to know the target section id
 * before the player picks it.
 */
public record OptionDto(int index, String description) {
}
