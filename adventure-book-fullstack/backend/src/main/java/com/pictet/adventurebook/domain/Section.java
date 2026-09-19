package com.pictet.adventurebook.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One numbered page of the book. {@code sectionNumber} is the id used in {@code gotoId}
 * references and in the source JSON files — it's kept separate from the JPA primary key
 * {@code id} because the same section number must be unique per book, not globally.
 *
 * <p>A BEGIN or NODE section needs at least one option to move the reader forward; an END
 * section is a stopping point and typically has none.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"book_id", "section_number"}))
@Getter
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long sectionNumber;

    @Lob
    @Column(nullable = false)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SectionType type;

    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "option_order")
    @Getter(AccessLevel.NONE)
    private List<Option> options = new ArrayList<>();

    @ManyToOne
    @JoinColumn(nullable = false)
    private Book book;

    protected Section() {
    }

    public Section(long sectionNumber, String text, SectionType type, List<Option> options) {
        this.sectionNumber = sectionNumber;
        this.text = text;
        this.type = type;
        setOptions(options);
    }

    /** The section's options, unmodifiable — see {@link Book#getSections()} for why. */
    public List<Option> getOptions() {
        return Collections.unmodifiableList(options);
    }

    public boolean isEnding() {
        return type == SectionType.END;
    }

    void setBook(Book book) {
        this.book = book;
    }

    private void setOptions(List<Option> options) {
        this.options = new ArrayList<>();
        if (options != null) {
            options.forEach(this::addOption);
        }
    }

    private void addOption(Option option) {
        options.add(option);
        option.setSection(this);
    }
}
