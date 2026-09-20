# Adventure Book Application

An interactive adventure book: browse a library of branching stories, play through one choice
at a time, lose (or regain) health along the way, and pick up a saved game later.

Java 21 / Spring Boot 3.3 backend, Angular 19 frontend, H2 for storage. No Docker, no database
to install.

---

## Contents

1. [Running it](#1-running-it)
2. [Architecture](#2-architecture)
3. [The five objectives, in order](#3-the-five-objectives-in-order)
4. [Book validation](#4-book-validation)
5. [Beyond the brief](#5-beyond-the-brief)
6. [Design decisions](#6-design-decisions)
7. [What went wrong along the way](#7-what-went-wrong-along-the-way)
8. [Tests](#8-tests)
9. [Notes on the sample data](#9-notes-on-the-sample-data)
10. [Known limitations](#10-known-limitations)
11. [A five-minute walkthrough](#11-a-five-minute-walkthrough)

---

## 1. Running it

You need **Java 21**, **Maven 3.8+** and **Node 20+**. The two halves run in separate
terminals.

### Backend

```bash
cd backend
mvn spring-boot:run
```

Serves on **http://localhost:8080**. On first start it creates `backend/data/` (an H2 file
database) and loads the four sample books from `src/main/resources/books/`. On later starts it
finds them already there and skips loading.

- API docs (Swagger UI): **http://localhost:8080/swagger-ui.html**
- To start from a clean library: stop the app and delete `backend/data/`.
- H2 web console, for looking at the data directly:
  `mvn spring-boot:run -Dspring-boot.run.profiles=dev` → http://localhost:8080/h2-console
  (JDBC URL `jdbc:h2:file:./data/adventure-book`, user `sa`, empty password).

### Frontend

```bash
cd frontend
npm install
npm start
```

Serves on **http://localhost:4200** and calls the backend on 8080. CORS is configured for
exactly that origin; override with `APP_CORS_ALLOWED_ORIGINS` to serve it elsewhere.

### Tests

```bash
cd backend  && mvn test       # 100 tests: unit, slice, and one full-application test
cd frontend && npm test       # 76 unit tests (Karma + Jasmine)
cd frontend && npm run e2e    # 43 end-to-end tests (Playwright), both servers must be running
```

`mvn test` also writes a JaCoCo report to `backend/target/site/jacoco/index.html`.

If Karma can't find a browser: `CHROME_BIN="/path/to/chrome.exe" npm test`. Playwright uses
the machine's installed Edge, so `npx playwright install` isn't needed.

---

## 2. Architecture

```
┌──────────────────────────────┐          ┌──────────────────────────────┐
│  Angular 19  :4200           │          │  Spring Boot 3.3  :8080      │
│                              │  HTTP    │                              │
│  features/                   │  JSON    │  web/         controllers    │
│    library/   the shelf      │ ───────▶ │  service/     coordination   │
│    game/      playing        │          │  domain/      the game rules │
│    admin/     managing       │ ◀─────── │  validation/  the five rules │
│    book-editor/ add & edit   │          │  repository/  Spring Data    │
│                              │          │                              │
│  core/                       │          └───────────────┬──────────────┘
│    services/  HTTP only      │                          │ JPA
│    models/    API mirrors    │          ┌───────────────▼──────────────┐
│    interceptors/ player id   │          │  H2, file-backed             │
└──────────────────────────────┘          │  backend/data/               │
                                          └──────────────────────────────┘
```

**Each layer knows only the one below it.** `web` doesn't know what a transaction is,
`service` doesn't know what an HTTP request is, and `domain` doesn't know a database exists.

### Where the game rules live

On the entity, not in a service:

```java
// GameSession.java
public void choose(int optionIndex) {
    requirePlaying();
    Option chosen = currentSection().getOptions().get(optionIndex);

    lastConsequence = chosen.getConsequence();
    applyHealthChange(...);
    currentSectionNumber = chosen.getGotoId();   // move first

    settleOutcome();                             // then decide what that move meant
}
```

Moving between sections, applying a consequence and deciding whether the game ended are all
operations on one object's own state — GRASP's Information Expert. `GameService` loads,
delegates and saves; it holds no rules. The practical result: the largest test class in the
project (14 tests on `GameSession`) needs no Spring context at all.

### Request flow, start to finish

```
Reader clicks "Begin Quest"
      │
      ▼
GamePageComponent ──▶ GameService (Angular) ──▶ POST /api/games
                                                     │
                                                     ▼
                                          GameController
                                                     │  reads X-Player-Id
                                                     ▼
                                          GameService (Spring)
                                             │  already playing this book?
                                             │    yes → return that session
                                             │    no  → load book, start one
                                             ▼
                                          GameSession.start(book, playerId)
                                                     │
                                                     ▼
                                          GameSessionRepository.save()  ──▶  H2
                                                     │
                                                     ▼
                                          GameSessionMapper.toDto()
                                                     │
      ┌──────────────────────────────────────────────┘
      ▼
Section text + choices on screen
```

Every later choice follows the same path through `POST /api/games/{id}/choices`, and each one
persists the session — which is why saving needs no separate request.

### API

| Method | Route | Purpose |
| :-- | :-- | :-- |
| GET | `/api/books` | List with search, difficulty filter, pagination |
| POST | `/api/books` | Submit a new book |
| GET | `/api/books/{id}` | Read a whole book, for editing |
| PUT | `/api/books/{id}` | Revise a book |
| DELETE | `/api/books/{id}` | Remove a book |
| POST | `/api/games` | Start (or resume) a game |
| GET | `/api/games` | This reader's resumable games |
| GET | `/api/games/{id}` | A game's current state |
| POST | `/api/games/{id}/choices` | Make a choice |
| POST | `/api/games/{id}/stop` | End a game deliberately |

Errors all share one shape, and list every reason at once rather than stopping at the first:

```json
{
  "timestamp": "2026-09-20T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "messages": ["Book has no ending section", "Section 2 is not an ending but has no options"]
}
```

---

## 3. The five objectives, in order

Built in the order the brief gives them. Each was finished and tested before the next began,
so the application was demonstrable at every stage — objective 1 was a usable library before
there was any way to play, objective 2 was a playable game before consequences existed.

### Step 0 — the foundation

Before a library could list anything: the domain model (`Book → Section → Option →
Consequence`), the validation rules, and `BookLoaderService` reading the sample JSONs at
startup through that same validator.

One deliberate difference from the JSON: a section's `id` becomes `sectionNumber`, separate
from the JPA primary key. Section numbers are only unique *within* a book — two books both
having a section 1 is normal — so it can't be the primary key.

### Step 1 — the library

**Backend.** `GET /api/books` with free-text search over title and author, difficulty filter
and pagination, built on Spring Data `Specification` so the filters compose instead of needing
one query method per combination.

The chapter count on each card comes from **one grouped query** over the whole page, not
`book.getSections().size()` per row. The listing never loads sections at all.

**Frontend.** `LibraryPageComponent`, with search, filter and pagination feeding **one** RxJS
pipeline rather than three — debounced 300 ms, with `switchMap` so a slow response for `"ca"`
can't land after the one for `"caverns"` and overwrite the grid.

### Step 2 — playing a game

`POST /api/games` starts a session on the book's single BEGIN section;
`POST /api/games/{id}/choices` moves it forward. `GamePageComponent` shows the section text
and its choices.

Loading a book to play takes **two queries**, not one fetch join — see
[Design decisions](#6-design-decisions).

### Step 3 — consequences, health, endings

Health starts at 10 and is clamped to 0–10. A choice carrying a consequence applies it and
keeps its text, so the reader is told *why* their health moved rather than watching a number
drop. Reaching zero is death; reaching an END section finishes the story.

The header carries what the brief asks for — a way to stop or pause, the book name, the life,
and saving progression:

| Control | What it does |
| :-- | :-- |
| ← Back to Library | Leaves straight away; the game keeps its place |
| 💾 Save Progress | Confirms progress is kept, and stays in the game |
| ⏸ Pause | Both at once — confirms the save, then steps out |
| ⏹ Stop | Ends the adventure for good, and asks first |

Only Stop can't be undone, so only Stop asks.

### Step 4 — saving progression

**This needed almost no new code, which is the interesting part.** Every choice already
persisted the session — that *is* what playing does — so a saved game is simply one whose
status is still `PLAYING`. There is no save endpoint, deliberately: a separate save action
would mean progress could be lost by not pressing it.

What was added: `GET /api/games` listing resumable games, and the "Continue Playing" area,
which collapses to the four most recent so it can't push the library off the screen.

**Each reader sees only their own.** The browser generates an id on first visit, keeps it in
local storage, and an interceptor sends it as `X-Player-Id`. Without it, every visitor shares
one list and resuming someone else's game takes it over.

### Step 5 — adding books

`BookEditorPageComponent` submits to `POST /api/books` and displays whatever the backend
rejected. It deliberately does **not** re-implement the book rules — no beginning/ending/
`gotoId` checks in TypeScript — because two implementations of the same rules drift apart.

---

## 4. Book validation

A book is rejected if it has no beginning or more than one, has no ending, points an option at
a section that doesn't exist, or leaves a non-ending section without options. Those are the
four rules from the brief. A fifth was added:

> **Section numbers must be unique within a book.** Without it, `findBySectionNumber` has no
> single answer: two sections numbered 7 mean a `gotoId: 7` routes readers unpredictably — the
> game would appear to work while quietly sending different people to different places.

Each rule is one class behind a `sealed` interface:

```java
public sealed interface ValidationRule
        permits SingleBeginningRule, HasEndingRule, ValidNextSectionIdRule,
                NonEndingHasOptionsRule, UniqueSectionNumberRule {

    List<String> check(Book book);
}
```

Sealed because adding a rule is a deliberate change to what "valid" means, not something
another package should slip in by implementing an interface.

**The same validator runs in both places** — at startup for the bundled books, and on
`POST /api/books` for submitted ones. There is one definition of "valid", not two that drift.

---

## 5. Beyond the brief

**Revising and removing books.** The brief asks only for adding one, which leaves a mistake
permanent: publish a book with a broken `gotoId` and it sits in the library forever. So
`/admin` also revises and removes them.

Management is kept off the library page on purpose. The library is the reader's shelf — the
suggested design shows it as somewhere to pick an adventure — and a delete control one click
from "Begin Quest" serves neither job.

Both operations end any game still in progress on that book, and say so before you confirm: a
reader's saved position is a section number in the version they started, so after a revision
that number may point somewhere else entirely. A revision is validated by exactly the same
rules as a new book.

**One game per book at a time.** Starting a game on a book already part-way through resumes it
rather than creating a second session, which would show up as two identical rows in Continue
Playing with no way to tell them apart.

---

## 6. Design decisions

| Decision | Alternative rejected | Why |
| :-- | :-- | :-- |
| Game rules on `GameSession` | Rules in the service | Information Expert; testable with `new`, no Spring |
| `sealed ValidationRule` | Open interface | Adding a rule should be deliberate |
| Two queries for book + options | One double fetch join | Two parallel collection joins produce a cross product |
| One validator, both paths | Separate seed/API validation | One definition of "valid" |
| No save endpoint | A button that writes | Saving is already what playing does |
| `FINISHED`, not `WON` | `WON` | An END section can be a bad ending |
| Move, then decide death | Decide, then stop | The reader sees the room they died in |
| `if/else` for the outcome | Strategy pattern | Two fixed outcomes with precedence; abstracting would hide the order |
| `@Version` (optimistic) | Pessimistic locking | Conflicts are rare; locking every read costs every request |
| Browser-generated `playerId` | Full authentication | Separates readers without inventing a user model |
| JPA + H2 | JSON files / NoSQL | The queries the app makes are relational |

### The cross product, in detail

Loading a book to play needs sections *and* options. The natural query is wrong:

```java
// Wrong: two parallel collection joins multiply each other
left join fetch b.sections s left join fetch s.options
```

A 12-section book came back with 21 sections, each duplicated — `distinct` on the root entity
does **not** dedupe a nested collection. The symptom was real: `beginnings()` returned 2
instead of 1, and games refused to start. The fix is two queries in the same persistence
context, which Hibernate merges. Both repositories document it, and an integration test guards
against it returning.

### Why JPA and not the JSON files

The input format doesn't have to be the storage model. The application searches by title and
author, filters by difficulty, paginates, counts sections per book, and ties saved progress to
a book — that is a relational model. Reading and rewriting JSON files on every choice would be
worse at all of it.

### Entities and Lombok

JPA entities use `@Getter` only — never `@Data` or `@EqualsAndHashCode`. Generated
equals/hashCode either drags in the mutable sections collection (equality changing as the
entity is populated) or needs hand-written exclusions, and it breaks the identity semantics
Hibernate proxies rely on. `Consequence` *is* a record, because as an `@Embeddable` it has no
identity of its own and is always fully built by its constructor.

---

## 7. What went wrong along the way

Every one of these has a regression test; none was only patched.

| What broke | The symptom | The cause |
| :-- | :-- | :-- |
| Cross product on fetch | Games refused to start: "Book must have exactly one beginning" | Two parallel `join fetch` duplicated every section, so `beginnings()` returned 2 |
| Search died after one query | The filter looked ignored from the second keystroke on | `distinctUntilChanged()` on a `Subject<void>` compared `undefined` to `undefined` |
| Blank editor page | A white screen, nothing in the console | A getter read `this.form` *during* `this.form`'s own construction |
| Consequence text dropped | Health fell 10 → 3 with no explanation | The books' flavour text was applied but never stored |
| 500 on a malformed request | "Something went wrong on our side" | No handler for an unreadable body; Swagger promised 400 |
| 500 when editing a book | The most common edit — fixing a typo — failed | Hibernate issued INSERTs before DELETEs, tripping the unique `(book_id, section_number)` |
| Section text lost on edit | Every section came back blank in the form | The loader never copied `text` |
| Duplicate games on one book | Two identical rows in Continue Playing | `POST /api/games` always created a session |
| Games shared between readers | One visitor's list showed another's game | Sessions had no owner |
| Racing choices lost a turn | Health inconsistent with the choices made | No optimistic locking |

Two are worth telling in full:

**The cross product.** `distinct` on the root entity does *not* dedupe a nested collection.
This is the kind of bug that looks like a data problem — a 12-section book returning 21
sections — and the same mistake existed in a second repository, found by deliberately looking
for the pattern once it was understood.

**The dead search, which the unit tests passed.** They mocked the service and asserted it was
called, which stays true even when the pipeline is dead after its first use. The end-to-end
test that now guards it types three different searches in a row. The lesson: a test that
asserts a call happened doesn't prove the thing still works the second time.

---

## 8. Tests

| Suite | Count | What it covers |
| :-- | :-- | :-- |
| Backend | 100 | Unit, slice, and one full-application integration test |
| Frontend unit | 76 | Components and services (Karma + Jasmine) |
| End-to-end | 43 | Real browser against the real API, no mocks |

Backend coverage is 94% of lines — 100% on `validation`, 99% on `domain`, which is where a bug
would be a business-rule bug.

**The full-application test is the one worth pointing at.** Everything else mocks at least one
seam; `ApplicationIntegrationTest` runs the real context, real seed loading, real validation
wiring and real HTTP, so it catches a book that stops loading or a rule Spring doesn't pick up.

The end-to-end suite covers all five objectives: browsing and searching, playing through to an
ending, dying, resuming, stopping, and publishing a book including every way it can be
rejected. It cleans up after itself, so it can be run repeatedly against the same database.

---

## 9. Notes on the sample data

The four JSON files provided with the brief needed fixes before they would load. The sender
was asked and confirmed these were fine to make:

- `dragon-quest.json` arrived empty — 0 bytes — and was written from scratch.
- All four contained a section `666` typed as `NODE` with no options, which breaks the brief's
  own fourth rule. Where it was reachable it became an `END` (the text reads like a bad
  ending); where nothing pointed at it, it was removed.
- `pirates-jade-sea.json` pointed an option at section `999`, which doesn't exist — corrected
  to `20`, the section its description clearly means.

The originals are untouched in `books/` at the project root; the fixed copies the application
loads are in `backend/src/main/resources/books/`.

The suggested design also shows a synopsis, genre tags and a reading-time estimate on each
card. None of that exists in the source data, so rather than invent it the cards show what the
books actually carry, plus a chapter count derived from the section count.

---

## 10. Known limitations

- **Readers are separated, but not authenticated.** The `playerId` keeps one reader's saved
  games apart from another's, but anyone can send any id, the same person on another device is
  a different reader, and `/admin` is open to whoever reaches it. The brief doesn't ask for
  accounts; real ones would replace the browser-generated id with one issued at login, which
  the rest of the code is already shaped for.
- **`ddl-auto: update` rather than versioned migrations.** Fine when the schema comes from the
  entities and the data is re-seedable, but adding a column meant deleting the local database —
  which a real deployment could not do. Flyway or Liquibase is the answer there.
- **Deleting a book destroys the games played on it.** A saved position means nothing without
  the sections it refers to, so there is nothing sensible to keep — but it does make a delete
  final, which is why it asks first.

---

## 11. A five-minute walkthrough

Start from a clean library: stop the backend, delete `backend/data/`, start it again. Running
the end-to-end suite leaves published books behind, and a shelf full of
`The Whispering Woods 1789822718297` undercuts the demonstration.

**1 — The library.** Search `Ashfell`: it matches the *author*, not just titles. Type quickly
and watch the network — one request goes out, not one per keystroke.

**2 — Playing, and dying.** Begin *The Crystal Caverns* and take this path:

| Choice | What to point at |
| :-- | :-- |
| Cross the rope bridge | No consequence; health stays at 10 |
| Try to jump to the other side | −7, and the banner explains *why*: "You land hard and twist your ankle." |
| Continue deeper into the cavern | The banner clears — a harmless choice doesn't leave a stale explanation |
| Swim across | −5 → 0. Death, with its cause named |

One honest caveat, worth saying before anyone spots it: the section you die in reads
*"shivering but alive"*. That is the sample book's own writing — it assumed you would arrive
with health to spare — not a bug in the status.

**3 — Saving, without a save button.** Back to the library: the game is under Continue
Playing. Resume it — same section, same health. No save request was ever made.

**4 — Managing the library.** Open *Manage Library*, edit a book, and show every field comes
back filled in. Then break it — change the END section to a NODE — and show the backend
refusing with its reason, on the same rules a new book faces.

**5 — The API.** `/swagger-ui.html`: every endpoint, with its error shapes.

### Questions worth having an answer ready for

| If they ask | The short answer |
| :-- | :-- |
| Why JPA and not the JSON files? | The queries the app makes are relational; input format ≠ storage model |
| Why is the game logic on the entity? | Information Expert — and it makes 14 tests run with no Spring |
| Where is the save endpoint? | There isn't one; every choice persists, so nothing can be lost by not pressing save |
| Why `FINISHED` and not `WON`? | An END section can be a bad ending; the app can't verify a victory |
| Why a Stop button when Figure 1 has none? | The brief's text asks for it — and for the life display the figure also omits |
| Why no Strategy for the end-of-turn branch? | Two fixed outcomes with a precedence; an interface would hide the order in a list |
| Why no cache? | It was removed: caching a managed entity graph with `open-in-view: false` is how `LazyInitializationException` happens |
| Is it safe for two users? | They can play simultaneously without interference, but the `playerId` identifies rather than authenticates — say so plainly |

---

## Also in this repository

`adventure-book-bdd/` is a sibling folder holding a Cucumber suite — 31 scenarios describing
the same rules in plain English, running **this backend's own domain classes**, so a change to
the game rules that breaks a scenario fails that build. It was a separate exercise and isn't
part of this submission; the backend's own 100 tests stand on their own.

```bash
cd backend && mvn install -DskipTests     # the suite compiles against this jar
cd ../../adventure-book-bdd && mvn test
```

That dependency is why `spring-boot-maven-plugin` gives its executable jar the `exec`
classifier: repackaging would otherwise replace the plain jar with a fat one whose classes sit
under `BOOT-INF/classes`, where another project can't compile against them. Running the
application is unaffected.
