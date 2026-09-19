package com.pictet.adventurebook.bdd.support;

import com.pictet.adventurebook.validation.BookValidator;
import com.pictet.adventurebook.validation.HasEndingRule;
import com.pictet.adventurebook.validation.NonEndingHasOptionsRule;
import com.pictet.adventurebook.validation.SingleBeginningRule;
import com.pictet.adventurebook.validation.UniqueSectionNumberRule;
import com.pictet.adventurebook.validation.ValidNextSectionIdRule;
import com.pictet.adventurebook.validation.ValidationRule;

import java.util.List;

/**
 * Builds the backend's {@link BookValidator} without a Spring context.
 *
 * <p>In the running application Spring injects every {@code @Component} implementing
 * {@link ValidationRule}, so a new rule is enforced the moment it is written. Here the list
 * is assembled by hand, which means a rule added to the backend would be silently missing
 * from these scenarios — the exact kind of drift this project exists to catch.
 *
 * <p>{@link #ruleCount()} closes that hole: {@code ValidationRule} is sealed, so its
 * permitted subclasses are a complete, compiler-enforced list, and a scenario asserts that
 * this factory wires up every one of them. Add a sixth rule to the backend and that
 * assertion fails until it is wired in here too.
 */
public final class Validators {

    private Validators() {
    }

    public static BookValidator bookValidator() {
        return new BookValidator(allRules());
    }

    public static List<ValidationRule> allRules() {
        return List.of(
                new SingleBeginningRule(),
                new HasEndingRule(),
                new ValidNextSectionIdRule(),
                new NonEndingHasOptionsRule(),
                new UniqueSectionNumberRule());
    }

    /** How many rules the backend declares, straight from the sealed interface. */
    public static int ruleCount() {
        return ValidationRule.class.getPermittedSubclasses().length;
    }
}
