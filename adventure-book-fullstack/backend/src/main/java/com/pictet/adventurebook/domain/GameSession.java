package com.pictet.adventurebook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;

import java.time.Instant;

/**
 * One player's progress through a book: which section they're on, how much health they
 * have left, and whether the game is still going. This is both "the game in progress"
 * (Objectives 2/3) and "the saved game" (Objective 4) — there's no separate save entity,
 * because saving progress is just persisting this same row, which it already is.
 *
 * <p>Choosing an option is modeled as a method on this entity rather than logic living in
 * a service, since moving between sections, applying a consequence, and deciding whether
 * the game just ended are all one operation on this object's own state — not something
 * that needs to reach into other aggregates.
 */
@Entity
@Getter
public class GameSession {

    public static final int STARTING_HEALTH = 10;
    public static final int MAX_HEALTH = 10;
    public static final int MIN_HEALTH = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Book book;

    @Column(nullable = false)
    private long currentSectionNumber;

    @Column(nullable = false)
    private int health;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameStatus status;

    /**
     * The consequence applied by the player's most recent choice, kept so the UI can tell
     * them <em>why</em> their health changed. Without it a player just watches the number
     * drop from 10 to 6 with no explanation, and the flavour text the books ship with
     * ("you cut yourself on a rusty nail") never reaches anyone. Null before the first
     * choice, and after any choice that carried no consequence.
     */
    @Embedded
    private Consequence lastConsequence;

    @Column(nullable = false)
    private Instant updatedAt;

    protected GameSession() {
    }

    /**
     * Starts a new session on a book's single BEGIN section, with full health.
     * @throws IllegalArgumentException if the book doesn't have exactly one beginning —
     *         callers are expected to only pass books that already passed
     *         {@link com.pictet.adventurebook.validation.BookValidator}.
     */
    public static GameSession start(Book book) {
        var beginnings = book.beginnings();
        if (beginnings.size() != 1) {
            throw new IllegalArgumentException("Book must have exactly one beginning to start a game");
        }

        GameSession session = new GameSession();
        session.book = book;
        session.currentSectionNumber = beginnings.getFirst().getSectionNumber();
        session.health = STARTING_HEALTH;
        session.status = GameStatus.PLAYING;
        session.updatedAt = Instant.now();
        return session;
    }

    /**
     * Applies the consequence (if any) of the option at {@code optionIndex} in the current
     * section, then moves to the section it points to. Ends the game as {@link
     * GameStatus#DEAD} if health drops to zero, or as {@link GameStatus#FINISHED} if the
     * new section is an ending.
     *
     * <p>A fatal choice still moves the player to the target section, so the story reads
     * correctly: they see where the choice led them and the consequence that killed them
     * there, rather than being left staring at the text they had already read.
     *
     * @throws IllegalStateException if the game already ended
     * @throws IndexOutOfBoundsException if {@code optionIndex} isn't a valid choice here
     */
    public void choose(int optionIndex) {
        if (status != GameStatus.PLAYING) {
            throw new IllegalStateException("Game already finished with status " + status);
        }

        Option chosen = currentSection().getOptions().get(optionIndex);

        lastConsequence = chosen.getConsequence();
        if (chosen.hasConsequence()) {
            applyHealthChange(chosen.getConsequence().signedValue());
        }

        currentSectionNumber = chosen.getGotoId();

        if (health <= MIN_HEALTH) {
            status = GameStatus.DEAD;
        } else if (currentSection().isEnding()) {
            status = GameStatus.FINISHED;
        }
        updatedAt = Instant.now();
    }

    /**
     * Stops the game deliberately, from the header's stop control. The session stays in the
     * database with its progress intact — it simply no longer shows up as resumable and
     * won't accept further choices.
     *
     * @throws IllegalStateException if the game had already ended
     */
    public void abandon() {
        if (status != GameStatus.PLAYING) {
            throw new IllegalStateException("Game already finished with status " + status);
        }
        status = GameStatus.ABANDONED;
        updatedAt = Instant.now();
    }

    public Section currentSection() {
        return book.findBySectionNumber(currentSectionNumber)
                .orElseThrow(() -> new IllegalStateException(
                        "Current section " + currentSectionNumber + " does not exist in book " + book.getId()));
    }

    private void applyHealthChange(int delta) {
        health = Math.max(MIN_HEALTH, Math.min(MAX_HEALTH, health + delta));
    }
}
