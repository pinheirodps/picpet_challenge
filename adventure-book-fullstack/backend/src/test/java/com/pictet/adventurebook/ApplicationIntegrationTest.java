package com.pictet.adventurebook;

import com.jayway.jsonpath.JsonPath;
import com.pictet.adventurebook.repository.BookRepository;
import com.pictet.adventurebook.validation.ValidationRule;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The one test that runs the whole application as it actually ships: real context, real
 * seed loading, real validation wiring, real HTTP. Everything else in the suite mocks at
 * least one seam, so this is what catches a book that stops loading, a validation rule
 * that isn't picked up by Spring, or an endpoint that only works against a mock service.
 *
 * <p>Runs against an in-memory database so it never touches the developer's H2 file.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:integration-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ApplicationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private List<ValidationRule> validationRules;

    @Test
    void everyValidationRuleIsDiscoveredBySpring() {
        // The sealed interface permits five implementations; if one is added without a
        // @Component (or vice versa) the validator silently stops enforcing it.
        assertThat(validationRules).hasSize(5);
        assertThat(validationRules).extracting(rule -> rule.getClass().getSimpleName())
                .containsExactlyInAnyOrder(
                        "SingleBeginningRule",
                        "HasEndingRule",
                        "ValidNextSectionIdRule",
                        "NonEndingHasOptionsRule",
                        "UniqueSectionNumberRule");
    }

    @Test
    void theSampleBooksAreSeededOnStartup() {
        assertThat(bookRepository.count()).isEqualTo(4);
    }

    @Test
    void theLibraryEndpointServesTheSeededBooksWithTheirSectionCounts() throws Exception {
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].sectionCount").isNumber())
                .andExpect(jsonPath("$.content[0].sectionCount", Matchers.greaterThan(0)));
    }

    @Test
    void aBookCanBePlayedFromItsBeginningThroughTheRealApi() throws Exception {
        Long bookId = bookRepository.findAll().getFirst().getId();

        String started = mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\": %d}".formatted(bookId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.health").value(10))
                .andExpect(jsonPath("$.status").value("PLAYING"))
                .andExpect(jsonPath("$.options").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long gameId = JsonPath.parse(started).read("$.gameId", Integer.class);

        mockMvc.perform(post("/api/games/%d/choices".formatted(gameId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionIndex\": 0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value((int) gameId));

        mockMvc.perform(get("/api/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gameId").value((int) gameId));

        mockMvc.perform(post("/api/games/%d/stop".formatted(gameId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABANDONED"));

        // A stopped game drops out of the resumable list.
        mockMvc.perform(get("/api/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void submittingAnInvalidBookIsRejectedWithEveryReason() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Broken",
                                  "author": "Nobody",
                                  "difficulty": "EASY",
                                  "sections": [
                                    {"id": 1, "text": "Only a node", "type": "NODE",
                                     "options": [{"description": "Nowhere", "gotoId": 99}]}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages").isArray())
                .andExpect(jsonPath("$.messages.length()").value(Matchers.greaterThan(1)));
    }

    /**
     * Reading a book back in full, then revising it and removing it.
     *
     * <p>The read matters more than it looks: the response is serialized after the service's
     * transaction has closed, and with {@code open-in-view: false} there is no second chance
     * to load a collection lazily. A book whose options are fetched by side effect inside the
     * service will serialize fine in a slice test and fail here, which is exactly what this
     * caught the first time it ran.
     */
    @Test
    void aBookCanBeReadInFullThenRevisedAndRemoved() throws Exception {
        String created = mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "A Draft", "author": "A Contributor", "difficulty": "EASY",
                                  "sections": [
                                    {"id": 1, "text": "The start", "type": "BEGIN",
                                     "options": [{"description": "Press on", "gotoId": 2,
                                                 "consequence": {"type": "LOSE_HEALTH", "value": 3,
                                                                 "text": "A branch catches your arm"}}]},
                                    {"id": 2, "text": "The end", "type": "END"}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int bookId = JsonPath.read(created, "$.id");

        // Every section and option comes back, consequence included.
        mockMvc.perform(get("/api/books/" + bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("A Draft"))
                .andExpect(jsonPath("$.gamesInProgress").value(0))
                .andExpect(jsonPath("$.sections.length()").value(2))
                .andExpect(jsonPath("$.sections[0].options[0].gotoId").value(2))
                .andExpect(jsonPath("$.sections[0].options[0].consequence.text")
                        .value("A branch catches your arm"));

        // A game in progress is reported, so the editor can warn before revising.
        mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\": %d}".formatted(bookId)))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/books/" + bookId))
                .andExpect(jsonPath("$.gamesInProgress").value(1));

        // Revising replaces the content and ends that game.
        mockMvc.perform(put("/api/books/" + bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "A Revised Draft", "author": "A Contributor", "difficulty": "HARD",
                                  "sections": [
                                    {"id": 5, "text": "A new start", "type": "BEGIN",
                                     "options": [{"description": "Onwards", "gotoId": 6}]},
                                    {"id": 6, "text": "A new end", "type": "END"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("A Revised Draft"));

        mockMvc.perform(get("/api/books/" + bookId))
                .andExpect(jsonPath("$.sections[0].id").value(5))
                .andExpect(jsonPath("$.gamesInProgress").value(0));

        // And it can be removed for good.
        mockMvc.perform(delete("/api/books/" + bookId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/books/" + bookId))
                .andExpect(status().isNotFound());
    }

    /**
     * Starting a game twice on the same book resumes rather than duplicating.
     *
     * <p>Worth running against the real database: the query behind this uses JPQL's
     * {@code limit}, which only exists from Hibernate 6, and a mocked repository would never
     * notice if it were wrong.
     */
    @Test
    void startingAGameTwiceOnOneBookResumesTheFirst() throws Exception {
        // Its own book, so no other test's games can be mistaken for this one's.
        String created = mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "A Book Played Twice", "author": "Nobody", "difficulty": "EASY",
                                  "sections": [
                                    {"id": 1, "text": "The start", "type": "BEGIN",
                                     "options": [{"description": "Onwards", "gotoId": 2}]},
                                    {"id": 2, "text": "The middle", "type": "NODE",
                                     "options": [{"description": "Finish", "gotoId": 3}]},
                                    {"id": 3, "text": "The end", "type": "END"}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int bookId = JsonPath.read(created, "$.id");

        String first = mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\": %d}".formatted(bookId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int firstId = JsonPath.read(first, "$.gameId");

        // Move it to the middle section, so resuming is visibly different from starting over.
        mockMvc.perform(post("/api/games/%d/choices".formatted(firstId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionIndex\": 0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLAYING"));

        // Starting again hands back the same game, still where it was left.
        mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\": %d}".formatted(bookId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameId").value(firstId))
                .andExpect(jsonPath("$.sectionText").value("The middle"));

        // Exactly one resumable game on this book, not two. Asserted per-book rather than as
        // a total, because these tests share a database and others start games of their own.
        String saved = mockMvc.perform(get("/api/games"))
                .andReturn().getResponse().getContentAsString();
        List<Integer> idsForThisBook = JsonPath.read(saved,
                "$[?(@.bookTitle == 'A Book Played Twice')].gameId");
        assertThat(idsForThisBook).containsExactly(firstId);

        // Stopping it frees the book to be played again from the start.
        mockMvc.perform(post("/api/games/%d/stop".formatted(firstId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\": %d}".formatted(bookId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameId").value(Matchers.not(firstId)))
                .andExpect(jsonPath("$.sectionText").value("The start"))
                .andExpect(jsonPath("$.health").value(10));

        // These tests share one database, so this one takes its book away again — deleting it
        // takes its games too. Other tests here assert on the seeded four and would fail if
        // this one left a fifth behind.
        mockMvc.perform(delete("/api/books/" + bookId))
                .andExpect(status().isNoContent());
    }

    @Test
    void revisingABookIntoAnInvalidOneIsRejected() throws Exception {
        mockMvc.perform(put("/api/books/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Broken Revision", "author": "Nobody", "difficulty": "EASY",
                                  "sections": [{"id": 1, "text": "Nowhere to go", "type": "NODE"}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages").isArray());
    }
}
