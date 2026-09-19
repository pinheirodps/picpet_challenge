package com.pictet.adventurebook.exception;

/** Thrown when a game session id doesn't exist. */
public class GameSessionNotFoundException extends RuntimeException {

    public GameSessionNotFoundException(Long gameId) {
        super("No game session found with id " + gameId);
    }
}
