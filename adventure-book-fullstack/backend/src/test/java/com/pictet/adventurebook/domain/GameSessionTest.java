package com.pictet.adventurebook.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameSessionTest {

    @Test
    void startPlacesTheSessionOnTheBeginningSectionWithFullHealth() {
        Book book = book(
                section(1, SectionType.BEGIN, optionTo(2)),
                section(2, SectionType.END)
        );

        GameSession session = GameSession.start(book, "test-player");

        assertThat(session.getCurrentSectionNumber()).isEqualTo(1);
        assertThat(session.getHealth()).isEqualTo(GameSession.STARTING_HEALTH);
        assertThat(session.getStatus()).isEqualTo(GameStatus.PLAYING);
    }

    @Test
    void startRejectsABookWithoutExactlyOneBeginning() {
        Book book = book(section(1, SectionType.NODE, optionTo(1)));

        assertThatThrownBy(() -> GameSession.start(book, "test-player"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void choosingAnOptionWithoutConsequenceJustMovesToTheNextSection() {
        Book book = book(
                section(1, SectionType.BEGIN, optionTo(2)),
                section(2, SectionType.NODE, optionTo(3)),
                section(3, SectionType.END)
        );
        GameSession session = GameSession.start(book, "test-player");

        session.choose(0);

        assertThat(session.getCurrentSectionNumber()).isEqualTo(2);
        assertThat(session.getHealth()).isEqualTo(GameSession.STARTING_HEALTH);
        assertThat(session.getStatus()).isEqualTo(GameStatus.PLAYING);
    }

    @Test
    void reachingAnEndSectionFinishesTheGame() {
        Book book = book(
                section(1, SectionType.BEGIN, optionTo(2)),
                section(2, SectionType.END)
        );
        GameSession session = GameSession.start(book, "test-player");

        session.choose(0);

        assertThat(session.getStatus()).isEqualTo(GameStatus.FINISHED);
    }

    @Test
    void aLoseHealthConsequenceReducesHealth() {
        Book book = book(
                section(1, SectionType.BEGIN, optionWithConsequence(2, ConsequenceType.LOSE_HEALTH, 4)),
                section(2, SectionType.END)
        );
        GameSession session = GameSession.start(book, "test-player");

        session.choose(0);

        assertThat(session.getHealth()).isEqualTo(6);
    }

    @Test
    void aGainHealthConsequenceNeverExceedsTheMaximum() {
        Book book = book(
                section(1, SectionType.BEGIN, optionWithConsequence(2, ConsequenceType.GAIN_HEALTH, 5)),
                section(2, SectionType.END)
        );
        GameSession session = GameSession.start(book, "test-player");

        session.choose(0);

        assertThat(session.getHealth()).isEqualTo(GameSession.MAX_HEALTH);
    }

    @Test
    void reachingZeroHealthKillsThePlayer() {
        Book book = book(
                section(1, SectionType.BEGIN, optionWithConsequence(2, ConsequenceType.LOSE_HEALTH, 10)),
                section(2, SectionType.NODE, optionTo(3)),
                section(3, SectionType.END)
        );
        GameSession session = GameSession.start(book, "test-player");

        session.choose(0);

        assertThat(session.getStatus()).isEqualTo(GameStatus.DEAD);
        assertThat(session.getHealth()).isZero();
        // A fatal choice still moves the reader, so the death screen can show where the
        // choice led them rather than the text they'd already read.
        assertThat(session.getCurrentSectionNumber()).isEqualTo(2);
    }

    @Test
    void deathTakesPrecedenceOverReachingAnEndingOnTheSameChoice() {
        Book book = book(
                section(1, SectionType.BEGIN, optionWithConsequence(2, ConsequenceType.LOSE_HEALTH, 10)),
                section(2, SectionType.END)
        );
        GameSession session = GameSession.start(book, "test-player");

        session.choose(0);

        assertThat(session.getStatus()).isEqualTo(GameStatus.DEAD);
    }

    @Test
    void theLastConsequenceIsKeptSoThePlayerCanBeToldWhyTheirHealthChanged() {
        Book book = book(
                section(1, SectionType.BEGIN, optionWithConsequence(2, ConsequenceType.LOSE_HEALTH, 4)),
                section(2, SectionType.NODE, optionTo(3)),
                section(3, SectionType.END)
        );
        GameSession session = GameSession.start(book, "test-player");
        assertThat(session.getLastConsequence()).isNull();

        session.choose(0);

        assertThat(session.getLastConsequence()).isNotNull();
        assertThat(session.getLastConsequence().text()).isEqualTo("consequence");
        assertThat(session.getLastConsequence().signedValue()).isEqualTo(-4);
    }

    @Test
    void aChoiceWithoutConsequenceClearsThePreviousOne() {
        Book book = book(
                section(1, SectionType.BEGIN, optionWithConsequence(2, ConsequenceType.LOSE_HEALTH, 2)),
                section(2, SectionType.NODE, optionTo(3)),
                section(3, SectionType.END)
        );
        GameSession session = GameSession.start(book, "test-player");
        session.choose(0);

        session.choose(0);

        assertThat(session.getLastConsequence()).isNull();
    }

    @Test
    void abandoningStopsTheGameAndRefusesFurtherChoices() {
        Book book = book(
                section(1, SectionType.BEGIN, optionTo(2)),
                section(2, SectionType.END)
        );
        GameSession session = GameSession.start(book, "test-player");

        session.abandon();

        assertThat(session.getStatus()).isEqualTo(GameStatus.ABANDONED);
        assertThatThrownBy(() -> session.choose(0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void abandoningAnAlreadyFinishedGameFails() {
        Book book = book(
                section(1, SectionType.BEGIN, optionTo(2)),
                section(2, SectionType.END)
        );
        GameSession session = GameSession.start(book, "test-player");
        session.choose(0);

        assertThatThrownBy(session::abandon).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aFinishedGameRejectsFurtherChoices() {
        Book book = book(
                section(1, SectionType.BEGIN, optionTo(2)),
                section(2, SectionType.END)
        );
        GameSession session = GameSession.start(book, "test-player");
        session.choose(0);

        assertThatThrownBy(() -> session.choose(0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void choosingAnOutOfRangeOptionIndexFails() {
        Book book = book(
                section(1, SectionType.BEGIN, optionTo(2)),
                section(2, SectionType.END)
        );
        GameSession session = GameSession.start(book, "test-player");

        assertThatThrownBy(() -> session.choose(5))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    private static Book book(Section... sections) {
        return new Book("Test Book", "Test Author", "EASY", List.of(sections));
    }

    private static Section section(long number, SectionType type, Option... options) {
        return new Section(number, "Section " + number, type, List.of(options));
    }

    private static Option optionTo(long gotoId) {
        return new Option("Go to " + gotoId, gotoId, null);
    }

    private static Option optionWithConsequence(long gotoId, ConsequenceType type, int value) {
        return new Option("Go to " + gotoId, gotoId, new Consequence(type, value, "consequence"));
    }
}
