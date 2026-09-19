package com.pictet.adventurebook.bdd.steps;

import com.pictet.adventurebook.bdd.support.InMemoryGameStore;
import com.pictet.adventurebook.domain.GameSession;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for Objective 4: saving progress automatically and resuming a game later. There's no
 * explicit save step because there's no explicit save action — every choice already saves,
 * matching the real backend. Shared assertions about the active game live in
 * {@link SharedGameAssertionSteps}.
 *
 * <p>The games are real {@link GameSession}s; only the storage around them is faked, by
 * {@link InMemoryGameStore}.
 */
public class GameProgressSteps {

    private final InMemoryGameStore store = new InMemoryGameStore();
    private final TestContext context;

    private long activeGameId;

    public GameProgressSteps(TestContext context) {
        this.context = context;
    }

    @Given("a game is started and saved")
    public void aGameIsStartedAndSaved() {
        GameSession game = GameSession.start(context.book());
        activeGameId = store.save(game);
        context.setActiveGame(game);
    }

    @Given("a second game is started and saved")
    public void aSecondGameIsStartedAndSaved() {
        store.save(GameSession.start(context.book()));
    }

    @Given("the player picks option {int} and the game is saved")
    public void thePlayerPicksOptionAndTheGameIsSaved(int optionIndex) {
        GameSession game = context.activeGame();
        game.choose(optionIndex);
        store.save(game);
    }

    @Given("the reader stops the game and it is saved")
    public void theReaderStopsTheGameAndItIsSaved() {
        GameSession game = context.activeGame();
        game.abandon();
        store.save(game);
    }

    @When("the saved game is resumed")
    public void theSavedGameIsResumed() {
        context.setActiveGame(store.findById(activeGameId).orElseThrow());
    }

    @Then("the saved games list should contain {int} game")
    @Then("the saved games list should contain {int} games")
    public void theSavedGamesListShouldContain(int expectedCount) {
        assertThat(store.findPlaying()).hasSize(expectedCount);
    }
}
