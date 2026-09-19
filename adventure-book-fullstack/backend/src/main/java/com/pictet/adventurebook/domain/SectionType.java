package com.pictet.adventurebook.domain;

/**
 * What role a section plays in the book. A book must have exactly one {@link #BEGIN} and
 * at least one {@link #END} — see {@link com.pictet.adventurebook.validation.BookValidator}.
 */
public enum SectionType {
    BEGIN,
    NODE,
    END
}
