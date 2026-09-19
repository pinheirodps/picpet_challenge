package com.pictet.adventurebook.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error body returned by every failed API call, so the frontend has one shape to
 * handle regardless of which endpoint or exception produced it.
 */
public record ErrorResponse(Instant timestamp, int status, String error, List<String> messages) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(Instant.now(), status, error, List.of(message));
    }

    public static ErrorResponse of(int status, String error, List<String> messages) {
        return new ErrorResponse(Instant.now(), status, error, messages);
    }
}
