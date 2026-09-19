package com.pictet.adventurebook.bdd.steps;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;

/**
 * The one step both the validation and gameplay features use to build a {@link
 * com.pictet.adventurebook.domain.Book} from a Gherkin table. Kept separate so it's defined
 * exactly once and shared through {@link TestContext} instead of duplicated per feature.
 */
public class SharedBookSteps {

    private final TestContext context;

    public SharedBookSteps(TestContext context) {
        this.context = context;
    }

    @Given("a book with the following sections:")
    public void aBookWithTheFollowingSections(DataTable table) {
        context.setBook(BookTableParser.toBook(table));
    }
}
