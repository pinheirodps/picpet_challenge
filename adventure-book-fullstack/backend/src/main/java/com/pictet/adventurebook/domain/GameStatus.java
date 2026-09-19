package com.pictet.adventurebook.domain;

/**
 * Where a game session stands right now. Once a session leaves {@link #PLAYING},
 * {@link GameSession} refuses further moves.
 *
 * <p>{@link #FINISHED} rather than "won": reaching an END section isn't necessarily a
 * victory — several sample books end badly (a whirlpool drags you into darkness, the
 * adventure simply stops), and the spec only ever calls these "ending sections".
 */
public enum GameStatus {

    /** The reader is still making choices. */
    PLAYING,

    /** Health hit zero — the adventure is over (spec: "the player dies"). */
    DEAD,

    /** The reader reached an END section, for better or worse. */
    FINISHED,

    /** The reader deliberately stopped the game from the header. */
    ABANDONED
}
