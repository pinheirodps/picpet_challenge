package com.pictet.adventurebook.bdd.steps;

import com.pictet.adventurebook.bdd.support.Validators;
import com.pictet.adventurebook.validation.BookValidator;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for the book validation rules, running the backend's own {@link BookValidator} with
 * every rule it ships — see {@link Validators} for how the rule list is assembled without a
 * Spring context, and how it is kept honest.
 */
public class BookValidationSteps {

    private final BookValidator validator = Validators.bookValidator();
    private final TestContext context;

    private List<String> errors;

    public BookValidationSteps(TestContext context) {
        this.context = context;
    }

    @When("the book is validated")
    public void theBookIsValidated() {
        errors = validator.validate(context.book());
    }

    @Then("the book should be valid")
    public void theBookShouldBeValid() {
        assertThat(errors).isEmpty();
    }

    @Then("the book should be invalid")
    public void theBookShouldBeInvalid() {
        assertThat(errors).isNotEmpty();
    }

    @And("the validation errors should include {string}")
    public void theValidationErrorsShouldInclude(String expectedError) {
        assertThat(errors).contains(expectedError);
    }

    @Then("every validation rule the application ships should be covered here")
    public void everyValidationRuleShouldBeCovered() {
        assertThat(Validators.allRules()).hasSize(Validators.ruleCount());
    }
}
