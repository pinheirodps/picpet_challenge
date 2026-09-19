package com.pictet.adventurebook.bdd.support;

import com.pictet.adventurebook.domain.GameSession;
import com.pictet.adventurebook.domain.GameStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * An in-memory stand-in for the backend's {@code GameSessionRepository}, so the save and
 * resume scenarios can run without a database.
 *
 * <p>This is the one piece these tests still fake. The game rules themselves come from the
 * real {@link GameSession}; what's replaced here is only the storage around it, and the rule
 * that decides which games are resumable — {@code status == PLAYING} — is small enough to
 * restate honestly. Faking the database rather than the domain keeps the scenarios fast and
 * still lets them fail when the shipped rules change.
 *
 * <p>A {@link LinkedHashMap} keeps games in touch order (each {@link #save} moves the game to
 * the end), which answers "most recently played first" without needing to sort on the
 * {@code updatedAt} column the real repository orders by.
 */
public class InMemoryGameStore {

    private final Map<Long, GameSession> gamesById = new LinkedHashMap<>();
    private long nextId = 1;

    public long save(GameSession session) {
        long id = idOf(session).orElseGet(() -> nextId++);
        gamesById.remove(id);
        gamesById.put(id, session);
        return id;
    }

    public Optional<GameSession> findById(long id) {
        return Optional.ofNullable(gamesById.get(id));
    }

    /** Every game still in progress, most recently touched first — the "resume" list. */
    public List<GameSession> findPlaying() {
        List<GameSession> playing = gamesById.values().stream()
                .filter(session -> session.getStatus() == GameStatus.PLAYING)
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.reverse(playing);
        return playing;
    }

    private Optional<Long> idOf(GameSession session) {
        return gamesById.entrySet().stream()
                .filter(entry -> entry.getValue() == session)
                .map(Map.Entry::getKey)
                .findFirst();
    }
}
