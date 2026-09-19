package com.pictet.adventurebook.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A full adventure book: its metadata plus every section it contains. Sections are owned
 * by the book (cascade + orphan removal) since a section never makes sense outside the
 * book it belongs to.
 *
 * <p>Only {@code @Getter} is generated here — no {@code @Data}/{@code @EqualsAndHashCode}.
 * Lombok's value-style equals/hashCode is a known trap on JPA entities: it either drags in
 * the mutable {@code sections} collection (equality changing as the entity is populated) or
 * needs hand-written exclusions, and it breaks the identity semantics Hibernate proxies
 * rely on. Entity equality here stays the JPA default (reference identity), which is what
 * every collection and cache in this codebase already assumes.
 */
@Entity
@Getter
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String author;

    private String difficulty;

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    @Getter(AccessLevel.NONE)
    private List<Section> sections = new ArrayList<>();

    protected Book() {
    }

    public Book(String title, String author, String difficulty, List<Section> sections) {
        this.title = title;
        this.author = author;
        this.difficulty = difficulty;
        setSections(sections);
    }

    /**
     * The book's sections, unmodifiable — every section has to be added through the
     * constructor so its back-reference to this book is set. Handing out the live list
     * would let a caller append a section whose {@code book} field is null, which only
     * surfaces later as a constraint violation on flush.
     */
    public List<Section> getSections() {
        return Collections.unmodifiableList(sections);
    }

    /**
     * Updates the book's metadata and drops every section it currently has.
     *
     * <p>The collection is cleared rather than reassigned, which matters for JPA:
     * {@code orphanRemoval} only deletes a child when it is removed from the collection
     * Hibernate is tracking, so assigning a brand new list would leave the old sections
     * orphaned in the database with a dangling book reference.
     *
     * <p>Replacing content is deliberately two calls — this and {@link #addSections} — so the
     * caller can flush the deletes before the inserts. A revision usually reuses the same
     * section numbers, and {@code (book_id, section_number)} is unique, so letting Hibernate
     * order the statements itself means the new rows are inserted while the old ones are
     * still there, and the constraint fires. See
     * {@link com.pictet.adventurebook.service.BookService#update}.
     */
    public void reviseMetadata(String title, String author, String difficulty) {
        this.title = title;
        this.author = author;
        this.difficulty = difficulty;
        this.sections.clear();
    }

    /** Adds the revised sections, each wired back to this book. */
    public void addSections(List<Section> newSections) {
        if (newSections != null) {
            newSections.forEach(this::addSection);
        }
    }

    /**
     * Finds a section by its book-local {@code sectionNumber} — the id used in
     * {@code gotoId} references, not the JPA primary key.
     */
    public Optional<Section> findBySectionNumber(long sectionNumber) {
        return sections.stream()
                .filter(s -> s.getSectionNumber() == sectionNumber)
                .findFirst();
    }

    public List<Section> beginnings() {
        return sections.stream()
                .filter(s -> s.getType() == SectionType.BEGIN)
                .toList();
    }

    public List<Section> endings() {
        return sections.stream()
                .filter(Section::isEnding)
                .toList();
    }

    private void setSections(List<Section> sections) {
        this.sections = new ArrayList<>();
        if (sections != null) {
            sections.forEach(this::addSection);
        }
    }

    private void addSection(Section section) {
        this.sections.add(section);
        section.setBook(this);
    }
}
