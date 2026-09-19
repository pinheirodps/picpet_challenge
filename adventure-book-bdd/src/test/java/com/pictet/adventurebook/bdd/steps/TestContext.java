package com.pictet.adventurebook.bdd.steps;

import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.GameSession;

/**
 * Scenario-scoped state shared between step classes. Cucumber's PicoContainer glue creates
 * one instance of this per scenario and injects the same one into every step class that
 * asks for it in its constructor, so a book built in one class — or a game advanced in one
 * class — can be read in another without resorting to static fields.
 *
 * <p>Both types here are the backend's own. {@link GameSession} mutates in place rather than
 * returning a new state, so there's no need to write it back after each choice.
 */
public class TestContext {

    private Book book;
    private GameSession activeGame;

    public TestContext() {
    }

    Book book() {
        return book;
    }

    void setBook(Book book) {
        this.book = book;
    }

    GameSession activeGame() {
        return activeGame;
    }

    void setActiveGame(GameSession activeGame) {
        this.activeGame = activeGame;
    }
}
