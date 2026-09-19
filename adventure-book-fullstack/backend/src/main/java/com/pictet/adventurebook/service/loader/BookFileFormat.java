package com.pictet.adventurebook.service.loader;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Shapes matching the raw JSON book files in {@code src/main/resources/books}. Kept
 * separate from the JPA entities in {@code domain} because the source files are looser
 * than the domain model: a section's {@code id} shows up as either a JSON number or a
 * JSON string depending on the file, and some files carry stray fields (like a top-level
 * {@code "type"} on the book itself) that aren't part of the documented schema.
 * {@link JsonIgnoreProperties} lets those extra fields pass through without failing
 * deserialization instead of the loader having to special-case each file.
 */
final class BookFileFormat {

    private BookFileFormat() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BookFile(String title, String author, String difficulty, List<SectionFile> sections) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SectionFile(FlexibleId id, String text, String type, List<OptionFile> options) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OptionFile(String description, FlexibleId gotoId, ConsequenceFile consequence) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ConsequenceFile(String type, FlexibleId value, String text) {
    }
}
