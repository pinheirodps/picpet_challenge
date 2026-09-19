package com.pictet.adventurebook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * The health effect of picking an option: how much it costs or restores, and the flavor
 * text shown to the player when it triggers. Embedded directly into {@link Option} rather
 * than mapped as its own entity, since a consequence never exists without an option and
 * is never queried on its own.
 *
 * <p>A record rather than a Lombok-{@code @Getter} class: unlike the JPA entities in this
 * package, an embeddable has no identity of its own and is always fully built through its
 * constructor, which is exactly what records are for. Hibernate 6 maps records as
 * embeddables directly.
 */
@Embeddable
public record Consequence(
        @Enumerated(EnumType.STRING) @Column(name = "consequence_type") ConsequenceType type,
        @Column(name = "consequence_value") int value,
        @Column(name = "consequence_text") String text
) {

    /**
     * The health delta this consequence applies, with the sign already resolved —
     * negative for {@link ConsequenceType#LOSE_HEALTH}, positive for
     * {@link ConsequenceType#GAIN_HEALTH}.
     */
    public int signedValue() {
        return type == ConsequenceType.LOSE_HEALTH ? -Math.abs(value) : Math.abs(value);
    }
}
