package com.pictet.adventurebook.bdd.steps;

import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.Consequence;
import com.pictet.adventurebook.domain.ConsequenceType;
import com.pictet.adventurebook.domain.Option;
import com.pictet.adventurebook.domain.Section;
import com.pictet.adventurebook.domain.SectionType;
import io.cucumber.datatable.DataTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns the Gherkin "sections" tables used across the feature files into a real
 * {@link Book}.
 *
 * <p>The backend's entities are built here through their public constructors, with no
 * database involved. They carry JPA annotations, but those only mean anything to Hibernate —
 * as plain objects the entities enforce their own invariants (a section's back-reference to
 * its book, an option's to its section) in the constructor, which is all these scenarios
 * need.
 *
 * <p>Each row is one section: an id, a type, optional text, and a comma-separated list of
 * options. A plain option is just a target section id (e.g. {@code "2"}). An option that
 * carries a consequence is written as {@code "description|gotoId|TYPE|value"}, with an
 * optional fifth field for the sentence shown to the reader
 * ({@code "description|gotoId|TYPE|value|text"}). That keeps the common case (no
 * consequence) readable while still letting a handful of scenarios exercise
 * LOSE_HEALTH/GAIN_HEALTH without a separate table format.
 */
final class BookTableParser {

    private BookTableParser() {
    }

    static Book toBook(DataTable table) {
        List<Map<String, String>> rows = table.asMaps();
        List<Section> sections = new ArrayList<>();

        for (Map<String, String> row : rows) {
            long sectionNumber = Long.parseLong(row.get("id").trim());
            SectionType type = SectionType.valueOf(row.get("type").trim());
            String text = row.getOrDefault("text", "Section " + sectionNumber);
            String rawOptions = row.getOrDefault("options", "");

            sections.add(new Section(sectionNumber, text, type, parseOptions(rawOptions)));
        }

        return new Book("Test Book", "Test Author", "EASY", sections);
    }

    private static List<Option> parseOptions(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        List<Option> options = new ArrayList<>();
        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            options.add(parseOption(trimmed));
        }
        return options;
    }

    private static Option parseOption(String entry) {
        if (!entry.contains("|")) {
            long gotoId = Long.parseLong(entry);
            return new Option("Go to " + gotoId, gotoId, null);
        }

        String[] parts = entry.split("\\|");
        String description = parts[0].trim();
        long gotoId = Long.parseLong(parts[1].trim());

        if (parts.length == 2) {
            return new Option(description, gotoId, null);
        }

        ConsequenceType type = ConsequenceType.valueOf(parts[2].trim());
        int value = Integer.parseInt(parts[3].trim());
        // Scenarios that don't care about the wording fall back to the option's own
        // description, so only the ones asserting on the text have to spell it out.
        String text = parts.length > 4 ? parts[4].trim() : description;
        return new Option(description, gotoId, new Consequence(type, value, text));
    }
}
