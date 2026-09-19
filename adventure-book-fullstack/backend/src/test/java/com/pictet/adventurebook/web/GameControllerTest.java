package com.pictet.adventurebook.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.Consequence;
import com.pictet.adventurebook.domain.ConsequenceType;
import com.pictet.adventurebook.domain.GameSession;
import com.pictet.adventurebook.domain.Option;
import com.pictet.adventurebook.domain.Section;
import com.pictet.adventurebook.domain.SectionType;
import com.pictet.adventurebook.exception.GameSessionNotFoundException;
import com.pictet.adventurebook.service.GameService;
import com.pictet.adventurebook.web.dto.ChooseOptionRequest;
import com.pictet.adventurebook.web.dto.StartGameRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameController.class)
class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GameService gameService;

    private Book book() {
        return new Book("The Crystal Caverns", "Evelyn Stormrider", "EASY", List.of(
                new Section(1, "You stand at the entrance", SectionType.BEGIN, List.of(new Option("Go in", 2, null))),
                new Section(2, "You made it out", SectionType.END, List.of())
        ));
    }

    @Test
    void startGameReturnsTheInitialState() throws Exception {
        GameSession session = GameSession.start(book());
        when(gameService.startGame(1L)).thenReturn(session);

        mockMvc.perform(post("/api/games")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new StartGameRequest(1L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookTitle").value("The Crystal Caverns"))
                .andExpect(jsonPath("$.health").value(10))
                .andExpect(jsonPath("$.maxHealth").value(10))
                .andExpect(jsonPath("$.status").value("PLAYING"))
                .andExpect(jsonPath("$.options[0].description").value("Go in"));
    }

    @Test
    void startGameRejectsAMissingBookId() throws Exception {
        mockMvc.perform(post("/api/games")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chooseReturnsUpdatedStateAfterWinning() throws Exception {
        GameSession session = GameSession.start(book());
        session.choose(0);
        when(gameService.choose(anyLong(), anyInt())).thenReturn(session);

        mockMvc.perform(post("/api/games/1/choices")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ChooseOptionRequest(0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.options").isEmpty());
    }

    @Test
    void chooseReturns404WhenSessionDoesNotExist() throws Exception {
        when(gameService.choose(anyLong(), anyInt())).thenThrow(new GameSessionNotFoundException(99L));

        mockMvc.perform(post("/api/games/99/choices")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ChooseOptionRequest(0))))
                .andExpect(status().isNotFound());
    }

    @Test
    void listSavedGamesReturnsInProgressSessionsAsSummaries() throws Exception {
        GameSession session = GameSession.start(book());
        when(gameService.listSavedGames()).thenReturn(List.of(session));

        mockMvc.perform(get("/api/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookTitle").value("The Crystal Caverns"))
                .andExpect(jsonPath("$[0].health").value(10));
    }

    @Test
    void choosingOnAFinishedGameReturns409WithTheUniformErrorShape() throws Exception {
        when(gameService.choose(anyLong(), anyInt()))
                .thenThrow(new IllegalStateException("Game already finished with status FINISHED"));

        mockMvc.perform(post("/api/games/1/choices")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ChooseOptionRequest(0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.messages[0]").value("Game already finished with status FINISHED"));
    }

    @Test
    void theResponseExplainsWhatTheLastChoiceDidToThePlayer() throws Exception {
        Book book = new Book("The Crystal Caverns", "Evelyn Stormrider", "EASY", List.of(
                new Section(1, "A narrow gap", SectionType.BEGIN, List.of(
                        new Option("Squeeze through", 2,
                                new Consequence(ConsequenceType.LOSE_HEALTH, 4, "You scrape your shoulder.")))),
                new Section(2, "A glittering chamber", SectionType.NODE, List.of(new Option("Look", 3, null))),
                new Section(3, "The way out", SectionType.END, List.of())
        ));
        GameSession session = GameSession.start(book);
        session.choose(0);
        when(gameService.choose(anyLong(), anyInt())).thenReturn(session);

        mockMvc.perform(post("/api/games/1/choices")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ChooseOptionRequest(0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.health").value(6))
                .andExpect(jsonPath("$.lastConsequence.type").value("LOSE_HEALTH"))
                .andExpect(jsonPath("$.lastConsequence.healthChange").value(-4))
                .andExpect(jsonPath("$.lastConsequence.text").value("You scrape your shoulder."));
    }

    @Test
    void stoppingAGameReturnsItsFinalState() throws Exception {
        GameSession session = GameSession.start(book());
        session.abandon();
        when(gameService.abandon(1L)).thenReturn(session);

        mockMvc.perform(post("/api/games/1/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABANDONED"))
                .andExpect(jsonPath("$.options").isEmpty());
    }

    @Test
    void stoppingAGameThatDoesNotExistReturns404() throws Exception {
        when(gameService.abandon(99L)).thenThrow(new GameSessionNotFoundException(99L));

        mockMvc.perform(post("/api/games/99/stop"))
                .andExpect(status().isNotFound());
    }

    // A malformed request is the caller's problem, so it has to come back as a 4xx. These
    // two used to fall through to the catch-all handler and return 500, telling the caller
    // the server had broken when in fact the request never arrived in a readable state.
    @Test
    void startGameWithNoBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/games").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.messages[0]").value("Request body is missing or is not valid JSON."));
    }

    @Test
    void startGameWithMalformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void startGameWithoutABookIdReturns400() throws Exception {
        mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value("bookId: must not be null"));
    }
}
