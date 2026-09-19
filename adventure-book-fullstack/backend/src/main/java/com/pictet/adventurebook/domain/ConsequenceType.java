package com.pictet.adventurebook.domain;

/**
 * Whether a consequence heals or hurts the player. The actual amount lives on
 * {@link Consequence#getValue()} as a positive number; this only decides the sign.
 */
public enum ConsequenceType {
    LOSE_HEALTH,
    GAIN_HEALTH
}
