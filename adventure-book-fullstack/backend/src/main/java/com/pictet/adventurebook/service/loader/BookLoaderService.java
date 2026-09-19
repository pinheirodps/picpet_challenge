package com.pictet.adventurebook.service.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pictet.adventurebook.domain.Book;
import com.pictet.adventurebook.domain.Consequence;
import com.pictet.adventurebook.domain.ConsequenceType;
import com.pictet.adventurebook.domain.Option;
import com.pictet.adventurebook.domain.Section;
import com.pictet.adventurebook.domain.SectionType;
import com.pictet.adventurebook.repository.BookRepository;
import com.pictet.adventurebook.service.loader.BookFileFormat.BookFile;
import com.pictet.adventurebook.service.loader.BookFileFormat.OptionFile;
import com.pictet.adventurebook.service.loader.BookFileFormat.SectionFile;
import com.pictet.adventurebook.validation.BookValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Loads the sample book JSON files from {@code src/main/resources/books} into the database
 * once, on application startup. The files are treated purely as seed data: after this runs,
 * the H2 database is the only source of truth, and re-running the loader on a fresh database
 * is the only way these books come back.
 *
 * <p>A file that fails to parse or fails {@link BookValidator} is logged and skipped rather
 * than stopping the application — one broken sample file (the assessment data ships with a
 * couple of intentionally invalid ones) shouldn't prevent the rest of the app from starting.
 */
@Component
public class BookLoaderService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BookLoaderService.class);

    private final BookRepository bookRepository;
    private final BookValidator bookValidator;
    private final ObjectMapper objectMapper;
    private final ResourcePatternResolver resourceResolver;
    private final String seedLocation;

    public BookLoaderService(
            BookRepository bookRepository,
            BookValidator bookValidator,
            ObjectMapper objectMapper,
            ResourcePatternResolver resourceResolver,
            @Value("${app.books.seed-location}") String seedLocation) {
        this.bookRepository = bookRepository;
        this.bookValidator = bookValidator;
        this.objectMapper = objectMapper;
        this.resourceResolver = resourceResolver;
        this.seedLocation = seedLocation;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (bookRepository.count() > 0) {
            log.info("Books already present in the database, skipping seed loading");
            return;
        }

        Resource[] resources = resourceResolver.getResources(seedLocation);
        Arrays.stream(resources).forEach(this::loadOne);
        log.info("Seed loading finished: {} book(s) in the library", bookRepository.count());
    }

    private void loadOne(Resource resource) {
        String filename = resource.getFilename();
        Book book;
        try {
            book = parseAndValidate(resource);
        } catch (IOException | RuntimeException e) {
            log.warn("Skipping {}: could not be parsed as a book ({})", filename, describeError(e));
            return;
        }

        if (book == null) {
            return;
        }

        try {
            bookRepository.save(book);
            log.info("Loaded book '{}' from {}", book.getTitle(), filename);
        } catch (RuntimeException e) {
            log.error("Failed to save book '{}' from {} — this book passed validation, "
                    + "the failure is on the database side", book.getTitle(), filename, e);
        }
    }

    /** Returns {@code null} (already logged) if the file is empty or fails validation. */
    private Book parseAndValidate(Resource resource) throws IOException {
        String filename = resource.getFilename();
        if (resource.contentLength() == 0) {
            log.warn("Skipping {}: file is empty", filename);
            return null;
        }

        BookFile file;
        try (InputStream in = resource.getInputStream()) {
            file = objectMapper.readValue(in, BookFile.class);
        }
        Book book = toBook(file);

        List<String> errors = bookValidator.validate(book);
        if (!errors.isEmpty()) {
            log.warn("Skipping {}: book failed validation: {}", filename, errors);
            return null;
        }
        return book;
    }

    private static String describeError(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private Book toBook(BookFile file) {
        List<Section> sections = file.sections().stream().map(this::toSection).toList();
        return new Book(file.title(), file.author(), file.difficulty(), sections);
    }

    private Section toSection(SectionFile file) {
        List<Option> options = file.options() == null
                ? List.of()
                : file.options().stream().map(this::toOption).toList();
        SectionType type = SectionType.valueOf(file.type());
        return new Section(file.id().value(), file.text(), type, options);
    }

    private Option toOption(OptionFile file) {
        Consequence consequence = file.consequence() == null ? null : toConsequence(file.consequence());
        return new Option(file.description(), file.gotoId().value(), consequence);
    }

    private Consequence toConsequence(BookFileFormat.ConsequenceFile file) {
        ConsequenceType type = ConsequenceType.valueOf(file.type());
        FlexibleId value = Objects.requireNonNull(file.value(), "consequence is missing its \"value\" field");
        return new Consequence(type, Math.toIntExact(value.value()), file.text());
    }
}
