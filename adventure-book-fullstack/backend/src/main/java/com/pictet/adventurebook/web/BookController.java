package com.pictet.adventurebook.web;

import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.service.BookService;
import com.pictet.adventurebook.web.dto.BookDetailDto;
import com.pictet.adventurebook.web.dto.BookDetailMapper;
import com.pictet.adventurebook.web.dto.BookSummaryDto;
import com.pictet.adventurebook.web.dto.BookSummaryMapper;
import com.pictet.adventurebook.web.dto.CreateBookMapper;
import com.pictet.adventurebook.web.dto.CreateBookRequest;
import com.pictet.adventurebook.web.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The library endpoints: listing books with search and filter (Objective 1), and managing
 * them (Objective 5, extra) — adding a book, reading one back in full, revising it and
 * removing it.
 *
 * <p>Revising and removing go beyond what the brief asks for. They're here because without
 * them a book submitted with a mistake is stuck in the library permanently, which makes the
 * add-a-book feature much less useful than it looks.
 */
@RestController
@Tag(name = "Books", description = "Browsing the adventure book library, and adding, revising or removing books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/api/books")
    @Operation(summary = "List books", description = "Returns a page of books, optionally filtered by a "
            + "free-text search on title/author and by exact difficulty.")
    public Page<BookSummaryDto> listBooks(
            @Parameter(description = "Matches against title or author, case-insensitive")
            @RequestParam(required = false) String query,
            @Parameter(description = "Exact difficulty match, e.g. EASY, MEDIUM, HARD")
            @RequestParam(required = false) String difficulty,
            @PageableDefault(size = 12) Pageable pageable) {
        return bookService.search(query, difficulty, pageable);
    }

    @PostMapping("/api/books")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a new book", description = "Validates the submitted book with the same rules "
            + "the built-in library uses (exactly one beginning, at least one ending, every option pointing "
            + "at a real section, every non-ending section having options, and unique section ids), and "
            + "adds it to the library if it passes.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Book accepted and added to the library"),
            @ApiResponse(responseCode = "400", description = "The book failed validation, or a required field "
                    + "was missing. Every reason is listed in the response's messages array.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public BookSummaryDto createBook(@Valid @RequestBody CreateBookRequest request) {
        Book book = bookService.create(CreateBookMapper.toBook(request));
        return BookSummaryMapper.toSummary(book, request.sections().size());
    }

    @GetMapping("/api/books/{bookId}")
    @Operation(summary = "Get a book in full", description = "Returns a book with every section and option, "
            + "in the same shape the create and update endpoints accept — this is what the editor loads to "
            + "revise an existing book. Also reports how many games are still in progress on it, so the "
            + "editor can warn that revising or deleting will end them.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The book, sections and options included"),
            @ApiResponse(responseCode = "404", description = "No book with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public BookDetailDto getBook(@PathVariable Long bookId) {
        Book book = bookService.loadPlayableBook(bookId);
        return BookDetailMapper.toDetail(book, bookService.countGamesInProgress(bookId));
    }

    @PutMapping("/api/books/{bookId}")
    @Operation(summary = "Revise a book", description = "Replaces a book's content with a revised version, "
            + "held to the same validation rules as a new book. Any game still in progress on this book is "
            + "ended: the reader's saved position is a section number in the old version, which after the "
            + "revision may point somewhere else entirely.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Book revised"),
            @ApiResponse(responseCode = "400", description = "The revised book failed validation, or a "
                    + "required field was missing. Every reason is listed in the messages array.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No book with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public BookSummaryDto updateBook(@PathVariable Long bookId, @Valid @RequestBody CreateBookRequest request) {
        Book book = bookService.update(bookId, CreateBookMapper.toBook(request));
        return BookSummaryMapper.toSummary(book, request.sections().size());
    }

    @DeleteMapping("/api/books/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a book", description = "Deletes a book and every game played on it — a "
            + "saved position means nothing without the sections it refers to. Callers should warn first; "
            + "the GET endpoint reports how many games are in progress.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Book removed"),
            @ApiResponse(responseCode = "404", description = "No book with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void deleteBook(@PathVariable Long bookId) {
        bookService.delete(bookId);
    }
}
