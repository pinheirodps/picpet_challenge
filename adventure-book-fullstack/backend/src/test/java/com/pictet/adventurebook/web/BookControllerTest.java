package com.pictet.adventurebook.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.Consequence;
import com.pictet.adventurebook.domain.ConsequenceType;
import com.pictet.adventurebook.domain.Option;
import com.pictet.adventurebook.domain.Section;
import com.pictet.adventurebook.domain.SectionType;
import com.pictet.adventurebook.exception.BookNotFoundException;
import com.pictet.adventurebook.exception.BookValidationException;
import com.pictet.adventurebook.service.BookService;
import com.pictet.adventurebook.web.dto.BookSummaryDto;
import com.pictet.adventurebook.web.dto.ConsequenceRequest;
import com.pictet.adventurebook.web.dto.CreateBookRequest;
import com.pictet.adventurebook.web.dto.OptionRequest;
import com.pictet.adventurebook.web.dto.SectionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookController.class)
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookService bookService;

    @Test
    void listBooksReturnsPagedSummaries() throws Exception {
        BookSummaryDto book = new BookSummaryDto(1L, "The Crystal Caverns", "Evelyn Stormrider", "EASY", 12);
        Page<BookSummaryDto> page = new PageImpl<>(List.of(book), PageRequest.of(0, 12), 1);
        when(bookService.search(isNull(), isNull(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("The Crystal Caverns"))
                .andExpect(jsonPath("$.content[0].sectionCount").value(12))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void listBooksPassesQueryAndDifficultyThrough() throws Exception {
        Page<BookSummaryDto> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 12), 0);
        when(bookService.search(eq("caverns"), eq("EASY"), any(Pageable.class))).thenReturn(emptyPage);

        mockMvc.perform(get("/api/books").param("query", "caverns").param("difficulty", "EASY"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"));
    }

    @Test
    void createBookReturns201WithTheSavedBookSummary() throws Exception {
        CreateBookRequest request = new CreateBookRequest("New Book", "Some Author", "EASY", List.of(
                new SectionRequest(1L, "Start", SectionType.BEGIN, List.of(
                        new OptionRequest("Go to 2", 2L, null))),
                new SectionRequest(2L, "End", SectionType.END, List.of())
        ));
        Book saved = new Book("New Book", "Some Author", "EASY", List.of());
        when(bookService.create(any(Book.class))).thenReturn(saved);

        mockMvc.perform(post("/api/books")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("New Book"));
    }

    @Test
    void createBookRejectsAMissingTitle() throws Exception {
        CreateBookRequest request = new CreateBookRequest(null, "Some Author", "EASY", List.of(
                new SectionRequest(1L, "Start", SectionType.BEGIN, List.of())
        ));

        mockMvc.perform(post("/api/books")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBookReturns400WhenValidationFails() throws Exception {
        CreateBookRequest request = new CreateBookRequest("Broken Book", "Some Author", "EASY", List.of(
                new SectionRequest(1L, "Only node, no beginning", SectionType.NODE, List.of(
                        new OptionRequest("Go to 1", 1L, new ConsequenceRequest(ConsequenceType.LOSE_HEALTH, 3, "ouch")))
                )
        ));
        when(bookService.create(any(Book.class)))
                .thenThrow(new BookValidationException(List.of("Book has no beginning section")));

        mockMvc.perform(post("/api/books")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value("Book has no beginning section"));
    }

    private Book playableBook() {
        return new Book("The Crystal Caverns", "Evelyn Stormrider", "EASY", List.of(
                new Section(1, "You stand at the entrance", SectionType.BEGIN, List.of(
                        new Option("Go in", 2, new Consequence(ConsequenceType.LOSE_HEALTH, 4, "You scrape a knee")))),
                new Section(2, "You made it out", SectionType.END, List.of())
        ));
    }

    private CreateBookRequest aValidRequest() {
        return new CreateBookRequest("Revised Title", "Some Author", "HARD", List.of(
                new SectionRequest(1L, "A new start", SectionType.BEGIN, List.of(
                        new OptionRequest("Onwards", 2L, null))),
                new SectionRequest(2L, "A new end", SectionType.END, List.of())
        ));
    }

    @Test
    void getBookReturnsTheWholeBookIncludingOptionsAndConsequences() throws Exception {
        when(bookService.loadPlayableBook(1L)).thenReturn(playableBook());
        when(bookService.countGamesInProgress(1L)).thenReturn(2L);

        mockMvc.perform(get("/api/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("The Crystal Caverns"))
                .andExpect(jsonPath("$.gamesInProgress").value(2))
                .andExpect(jsonPath("$.sections.length()").value(2))
                .andExpect(jsonPath("$.sections[0].id").value(1))
                .andExpect(jsonPath("$.sections[0].options[0].gotoId").value(2))
                .andExpect(jsonPath("$.sections[0].options[0].consequence.type").value("LOSE_HEALTH"))
                .andExpect(jsonPath("$.sections[0].options[0].consequence.text").value("You scrape a knee"))
                .andExpect(jsonPath("$.sections[1].options").isEmpty());
    }

    @Test
    void getBookReturns404WhenItDoesNotExist() throws Exception {
        when(bookService.loadPlayableBook(99L)).thenThrow(new BookNotFoundException(99L));

        mockMvc.perform(get("/api/books/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateBookReturnsTheRevisedSummary() throws Exception {
        when(bookService.update(eq(1L), any(Book.class))).thenReturn(playableBook());

        mockMvc.perform(put("/api/books/1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(aValidRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionCount").value(2));
    }

    @Test
    void updateBookReturns400WhenTheRevisionFailsValidation() throws Exception {
        when(bookService.update(eq(1L), any(Book.class)))
                .thenThrow(new BookValidationException(List.of("Book has no ending section")));

        mockMvc.perform(put("/api/books/1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(aValidRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value("Book has no ending section"));
    }

    @Test
    void updateBookReturns404WhenTheBookDoesNotExist() throws Exception {
        when(bookService.update(eq(99L), any(Book.class))).thenThrow(new BookNotFoundException(99L));

        mockMvc.perform(put("/api/books/99")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(aValidRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteBookReturns204() throws Exception {
        mockMvc.perform(delete("/api/books/1"))
                .andExpect(status().isNoContent());

        verify(bookService).delete(1L);
    }

    @Test
    void deleteBookReturns404WhenTheBookDoesNotExist() throws Exception {
        doThrow(new BookNotFoundException(99L)).when(bookService).delete(99L);

        mockMvc.perform(delete("/api/books/99"))
                .andExpect(status().isNotFound());
    }
}
