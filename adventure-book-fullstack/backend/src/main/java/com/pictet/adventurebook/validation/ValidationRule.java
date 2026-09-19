package com.pictet.adventurebook.validation;

import com.pictet.adventurebook.domain.Book;

import java.util.List;

/**
 * One condition from the spec that can make a book invalid. Each implementation checks a
 * single thing and returns a human-readable reason for every violation it finds — an empty
 * list means the rule has nothing to complain about.
 *
 * <p>Kept as separate small classes (Strategy pattern) instead of one big validator method
 * so each rule is easy to test on its own. Sealed to this fixed set — the four rules from
 * the spec plus {@link UniqueSectionNumberRule}, needed for {@link
 * Book#findBySectionNumber} to be unambiguous — because adding a rule is a deliberate
 * change to what "valid" means, not something a class elsewhere should be able to slip in
 * unnoticed by just implementing the interface. Widening this list is one edit to the
 * {@code permits} clause away.
 */
public sealed interface ValidationRule
        permits SingleBeginningRule, HasEndingRule, ValidNextSectionIdRule, NonEndingHasOptionsRule,
        UniqueSectionNumberRule {

    List<String> check(Book book);
}
