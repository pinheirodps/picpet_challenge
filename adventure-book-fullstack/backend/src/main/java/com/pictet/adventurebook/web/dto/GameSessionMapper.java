package com.pictet.adventurebook.web.dto;

import com.pictet.adventurebook.domain.Consequence;
import com.pictet.adventurebook.domain.GameSession;
import com.pictet.adventurebook.domain.GameStatus;
import com.pictet.adventurebook.domain.Option;
import com.pictet.adventurebook.domain.Section;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Converts a {@link GameSession} into the shape the game endpoints return. Options are
 * only included while the game is still {@link GameStatus#PLAYING} — an ended game has
 * nothing left to choose.
 */
public final class GameSessionMapper {

    private GameSessionMapper() {
    }

    public static SavedGameDto toSavedGameDto(GameSession session) {
        return new SavedGameDto(
                session.getId(),
                session.getBook().getTitle(),
                session.getHealth(),
                session.getUpdatedAt()
        );
    }

    public static GameSessionDto toDto(GameSession session) {
        Section section = session.currentSection();
        List<OptionDto> options = session.getStatus() == GameStatus.PLAYING
                ? toOptionDtos(section)
                : List.of();

        return new GameSessionDto(
                session.getId(),
                session.getBook().getTitle(),
                session.getHealth(),
                GameSession.MAX_HEALTH,
                session.getStatus(),
                section.getText(),
                options,
                toConsequenceDto(session.getLastConsequence())
        );
    }

    private static ConsequenceDto toConsequenceDto(Consequence consequence) {
        return consequence == null
                ? null
                : new ConsequenceDto(consequence.type(), consequence.signedValue(), consequence.text());
    }

    private static List<OptionDto> toOptionDtos(Section section) {
        List<Option> sectionOptions = section.getOptions();
        return IntStream.range(0, sectionOptions.size())
                .mapToObj(i -> new OptionDto(i, sectionOptions.get(i).getDescription()))
                .toList();
    }
}
