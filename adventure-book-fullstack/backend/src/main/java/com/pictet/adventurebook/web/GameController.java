package com.pictet.adventurebook.web;

import com.pictet.adventurebook.domain.GameSession;
import com.pictet.adventurebook.service.GameService;
import com.pictet.adventurebook.web.dto.ChooseOptionRequest;
import com.pictet.adventurebook.web.dto.ErrorResponse;
import com.pictet.adventurebook.web.dto.GameSessionDto;
import com.pictet.adventurebook.web.dto.GameSessionMapper;
import com.pictet.adventurebook.web.dto.SavedGameDto;
import com.pictet.adventurebook.web.dto.StartGameRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Playing a game (Objectives 2 and 3): starting one, reading its current state, making
 * choices that move it forward until it's finished or the player dies, and stopping it
 * deliberately. Also lists in-progress games so the player can resume one (Objective 4).
 */
@RestController
@Tag(name = "Games", description = "Starting, playing and resuming an adventure book")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("/api/games")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a game", description = "Starts a new play session on the given book's "
            + "beginning section, with full health.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Game started"),
            @ApiResponse(responseCode = "400", description = "No bookId given, or the book can't be played",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No book with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public GameSessionDto startGame(@Valid @RequestBody StartGameRequest request) {
        GameSession session = gameService.startGame(request.bookId());
        return GameSessionMapper.toDto(session);
    }

    @GetMapping("/api/games")
    @Operation(summary = "List saved games", description = "Returns every game still in progress, most "
            + "recently played first, so the player can resume one. A game is \"saved\" automatically on "
            + "every choice — there's no separate save action.")
    public List<SavedGameDto> listSavedGames() {
        return gameService.listSavedGames().stream().map(GameSessionMapper::toSavedGameDto).toList();
    }

    @GetMapping("/api/games/{gameId}")
    @Operation(summary = "Get game state", description = "Returns the current section, health and "
            + "available choices for an in-progress or finished game.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The game's current state"),
            @ApiResponse(responseCode = "404", description = "No game with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public GameSessionDto getGame(@PathVariable Long gameId) {
        GameSession session = gameService.getSession(gameId);
        return GameSessionMapper.toDto(session);
    }

    @PostMapping("/api/games/{gameId}/choices")
    @Operation(summary = "Make a choice", description = "Picks one of the current section's options, "
            + "applies its consequence if it has one, and moves to the next section.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The game's state after the choice"),
            @ApiResponse(responseCode = "400", description = "The option index isn't valid for this section",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No game with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The game has already ended",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public GameSessionDto choose(@PathVariable Long gameId, @Valid @RequestBody ChooseOptionRequest request) {
        GameSession session = gameService.choose(gameId, request.optionIndex());
        return GameSessionMapper.toDto(session);
    }

    @PostMapping("/api/games/{gameId}/stop")
    @Operation(summary = "Stop a game", description = "Deliberately ends a game from the header's stop "
            + "control. The session keeps its progress but stops being resumable and accepts no more choices.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The game's final state"),
            @ApiResponse(responseCode = "404", description = "No game with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The game had already ended",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public GameSessionDto stopGame(@PathVariable Long gameId) {
        GameSession session = gameService.abandon(gameId);
        return GameSessionMapper.toDto(session);
    }
}
