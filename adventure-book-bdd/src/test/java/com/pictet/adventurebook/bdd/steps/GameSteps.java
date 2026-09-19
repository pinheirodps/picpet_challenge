package com.pictet.adventurebook.bdd.steps;

import com.pictet.adventurebook.domain.GameSession;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for Objectives 2/3: starting a game and playing through it, against the backend's
 * own {@link GameSession}. Shared assertions about the resulting state (current section,
 * status, health, last consequence) live in {@link SharedGameAssertionSteps}, which reads
 * {@link TestContext#activeGame()} — this class's job is only to advance that game.
 *
 * <p>{@code GameSession} applies a choice to itself, so these steps don't reassign the
 * active game after each move the way a value-style engine would require.
 */
public class GameSteps {

    private final TestContext context;

    private boolean lastAttemptRejected;

    public GameSteps(TestContext context) {
        this.context = context;
    }

    @Given("a game is started")
    public void aGameIsStarted() {
        context.setActiveGame(GameSession.start(context.book()));
    }

    @Given("the player picks option {int}")
    public void thePlayerPicksOption(int optionIndex) {
        context.activeGame().choose(optionIndex);
    }

    @Given("the reader stops the game")
    public void theReaderStopsTheGame() {
        context.activeGame().abandon();
    }

    @When("the player tries to pick option {int} again")
    public void thePlayerTriesToPickOptionAgain(int optionIndex) {
        try {
            context.activeGame().choose(optionIndex);
            lastAttemptRejected = false;
        } catch (IllegalStateException expected) {
            lastAttemptRejected = true;
        }
    }

    @Then("the attempt should be rejected")
    public void theAttemptShouldBeRejected() {
        assertThat(lastAttemptRejected).isTrue();
    }
}
