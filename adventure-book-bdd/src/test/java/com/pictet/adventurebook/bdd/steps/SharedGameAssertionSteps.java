package com.pictet.adventurebook.bdd.steps;

import com.pictet.adventurebook.domain.Consequence;
import com.pictet.adventurebook.domain.ConsequenceType;
import com.pictet.adventurebook.domain.GameStatus;
import io.cucumber.java.en.Then;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assertions about "whichever game is currently active" — shared between {@link GameSteps}
 * (Objectives 2/3, a single game per scenario) and {@link GameProgressSteps} (Objective 4,
 * where a resumed game becomes the active one). Both update {@link TestContext#setActiveGame}
 * as they go, so these steps never need to know which feature is running.
 */
public class SharedGameAssertionSteps {

    private final TestContext context;

    public SharedGameAssertionSteps(TestContext context) {
        this.context = context;
    }

    @Then("the current section should be {long}")
    public void theCurrentSectionShouldBe(long sectionNumber) {
        assertThat(context.activeGame().currentSection().getSectionNumber()).isEqualTo(sectionNumber);
    }

    @Then("the game status should be {word}")
    public void theGameStatusShouldBe(String status) {
        assertThat(context.activeGame().getStatus()).isEqualTo(GameStatus.valueOf(status));
    }

    @Then("the player's health should be {int}")
    public void thePlayersHealthShouldBe(int expectedHealth) {
        assertThat(context.activeGame().getHealth()).isEqualTo(expectedHealth);
    }

    @Then("the player's health should be at most {int}")
    public void thePlayersHealthShouldBeAtMost(int maxHealth) {
        assertThat(context.activeGame().getHealth()).isLessThanOrEqualTo(maxHealth);
    }

    @Then("the last consequence should be {word} of {int}")
    public void theLastConsequenceShouldBe(String type, int value) {
        Consequence consequence = context.activeGame().getLastConsequence();
        assertThat(consequence).isNotNull();
        assertThat(consequence.type()).isEqualTo(ConsequenceType.valueOf(type));
        assertThat(consequence.value()).isEqualTo(value);
    }

    @Then("the last consequence text should be {string}")
    public void theLastConsequenceTextShouldBe(String expectedText) {
        Consequence consequence = context.activeGame().getLastConsequence();
        assertThat(consequence).isNotNull();
        assertThat(consequence.text()).isEqualTo(expectedText);
    }

    @Then("there should be no last consequence")
    public void thereShouldBeNoLastConsequence() {
        assertThat(context.activeGame().getLastConsequence()).isNull();
    }
}
