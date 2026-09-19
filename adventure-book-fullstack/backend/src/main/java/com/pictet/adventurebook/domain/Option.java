package com.pictet.adventurebook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;

/**
 * One choice offered at the end of a section: what it says, which section id it leads to,
 * and the optional health consequence of picking it. The {@code gotoId} is stored as plain
 * data, not a foreign key to another {@link Section} — a book can (and in the sample data,
 * sometimes does) reference a section id that doesn't exist, and that's exactly what
 * {@link com.pictet.adventurebook.validation.ValidNextSectionIdRule} needs to catch.
 */
@Entity
@Getter
public class Option {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private long gotoId;

    @Embedded
    private Consequence consequence;

    @ManyToOne
    private Section section;

    protected Option() {
    }

    public Option(String description, long gotoId, Consequence consequence) {
        this.description = description;
        this.gotoId = gotoId;
        this.consequence = consequence;
    }

    public boolean hasConsequence() {
        return consequence != null;
    }

    void setSection(Section section) {
        this.section = section;
    }
}
