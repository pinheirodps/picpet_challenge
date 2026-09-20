# Adventure Book Application

An interactive adventure book: browse a library of branching stories, play through one
choice at a time, lose (or regain) health along the way, and pick up a saved game later.

Java 21 / Spring Boot 3.3 backend, Angular 19 frontend, H2 for storage.

---

## Running it

You need **Java 21**, **Maven 3.8+** and **Node 20+**. Nothing else — no Docker, no database
to install. The two halves run in separate terminals.

### Backend

```bash
cd backend
mvn spring-boot:run
```

Serves on **http://localhost:8080**. On first start it creates `backend/data/` (an H2 file
database) and loads the four sample books from `src/main/resources/books/`. On later starts
it finds the books already there and skips loading.

- API docs (Swagger UI): http://localhost:8080/swagger-ui.html
- To start from a clean library, stop the app and delete `backend/data/`.
- For the H2 web console, run with the `dev` profile:
  `mvn spring-boot:run -Dspring-boot.run.profiles=dev` → http://localhost:8080/h2-console
  (JDBC URL `jdbc:h2:file:./data/adventure-book`, user `sa`, empty password).

### Frontend

```bash
cd frontend
npm install
npm start
```

Serves on **http://localhost:4200** and talks to the backend on 8080 (CORS is configured for
exactly that origin; override with `APP_CORS_ALLOWED_ORIGINS` if you serve it elsewhere).

### Tests

```bash
cd backend  && mvn test       # 100 tests: unit, slice and one full-application integration test
cd frontend && npm test       # 76 unit tests (Karma + Jasmine)
cd frontend && npm run e2e    # 43 end-to-end tests (Playwright), both servers must be running
```

`mvn test` also writes a JaCoCo report to `backend/target/site/jacoco/index.html` (94% of
lines); `npm run test:coverage` does the same for the frontend, into `frontend/coverage/`.

For a by-hand pass over every rule in the brief, `doc/manual-test-guide.md` walks through
the whole application in about 20 minutes.

The end-to-end suite drives the real browser against the real API — no mocks — and covers
all five objectives: browsing and searching the library, playing a book through to an
ending, dying, resuming a saved game, stopping one, and publishing a new book (including
the rejections when it breaks a validation rule).

If Karma can't find a browser, point it at one explicitly:

```bash
CHROME_BIN="/path/to/chrome.exe" npm test
```

Playwright uses the machine's installed Edge (`channel: 'msedge'`), so `npx playwright
install` isn't needed.

The E2E suite cleans up after itself — every book it publishes, it also deletes — so it can be
run repeatedly against the same database. To start from the four sample books regardless, stop
the backend and delete `backend/data/`.

---

## The five objectives

Built in the order the brief gives them. Each one was finished and tested before the next was
started, so the application was working end to end at every step — objective 1 was a usable
library before there was any way to play, objective 2 was a playable game before consequences
existed, and so on.

| # | Objective | Where | Built on |
| :-- | :-- | :-- | :-- |
| 1 | Home page listing all books, with search and filter | `LibraryPageComponent`, `GET /api/books` | The domain model and validation, loaded from the sample JSONs |
| 2 | Start a game and make basic choices | `GamePageComponent`, `POST /api/games`, `POST /api/games/{id}/choices` | Objective 1's books, now playable |
| 3 | Consequences, health points, game end | `GameSession.choose()` — the domain owns this | Objective 2's turn loop, which gained health and endings |
| 4 | Save the player's progression | Automatic: every choice persists the session. `GET /api/games` lists resumable ones | Nothing new — objective 2 already persisted every turn |
| 5 | Add new books | `BookEditorPageComponent`, `POST /api/books` | Objective 1's validation rules, reused unchanged |

The right-hand column is the point: each objective reused what the previous one built rather
than adding a parallel mechanism. Objective 4 needed no new storage because saving was already
what playing did, and objective 5 needed no new validation because the rules that vet the
sample books vet a submitted one identically.

**One game per book at a time.** Starting a game on a book that's already part-way through
resumes it instead of creating a second session. Two games on one book show up in "Continue
Playing" as identical rows — same title, often the same health — with no way to tell them
apart, and the reader who pressed "begin" a second time by mistake has no idea which one holds
their progress. Playing a book twice at once is still possible: stop the first game and start
again, so the rarer intent is the one that takes the extra step.

**Each reader sees only their own saved games.** The browser generates an id on first visit,
keeps it in local storage, and sends it as `X-Player-Id`; the backend stores it on the session
and filters the resume list by it. This identifies a reader without authenticating one —
anyone can send any id — but without it every visitor shares one list and resuming someone
else's game takes it over.

**Two choices can't overwrite each other.** `GameSession` carries a `@Version` column, so a
second write to the same game while the first is in flight fails with 409 rather than silently
discarding a turn.

`doc/presentation-guide.md` walks through how each step was reached, what broke along the way,
and why the design decisions went the way they did. The same material is in Portuguese across
`doc/apresentacao-pt.md`, `doc/arquitetura-pt.md` and `doc/revisao-tecnica-pt.md`.

### Beyond the brief: revising and removing books

The brief asks only for adding a book. That leaves a mistake permanent — publish a book with a
broken `gotoId` and it sits in the library forever — so the same screen also revises and
removes them, at `/admin`.

Management is kept off the library page deliberately. The library is the reader's shelf, and
the suggested design shows it as somewhere to pick an adventure; a delete control one click
from "Begin Quest" serves neither job. Everything that changes the catalogue lives in one
place instead, which is also the honest separation: reading and maintaining are different
jobs with different risks.

Both operations end any game still in progress on that book, and say so before you confirm. A
reader's saved position is a section number in the version they started on — once the sections
change, that number may point somewhere else entirely, and silently resuming into a rewritten
story is worse than being told the book changed. A revision is validated by exactly the same
rules as a new book, so editing can't sneak an unplayable book past them.

`/admin` has no authentication in front of it. The brief doesn't ask for accounts and
inventing a user model wasn't the point of the exercise, but in anything real this screen is
the first thing that would sit behind a login.

### Book validation

A book is rejected if it has no beginning or more than one, has no ending, points an option
at a section that doesn't exist, or leaves a non-ending section without options — the four
rules from the brief. A fifth was added: **section ids must be unique within a book**,
because without it a `gotoId` has no single destination and the game routes players
unpredictably.

Each rule is its own class behind a sealed `ValidationRule` interface, and the same
`BookValidator` runs for both the bundled sample books and anything submitted through the
editor. A book is either playable or rejected with every reason listed, no matter where it
came from.

---

## How it's put together

```
backend/src/main/java/com/pictet/adventurebook/
├── domain/       Book → Section → Option → Consequence, plus GameSession
├── validation/   the five rules and the validator that runs them
├── repository/   Spring Data JPA, with deliberate fetch strategies (see below)
├── service/      transaction boundaries and coordination; no game rules live here
│   └── loader/   reads the sample JSON files into the database at startup
├── web/          controllers and DTOs — nothing but request/response shaping
└── exception/    domain exceptions and the handler that maps them to HTTP

frontend/src/app/
├── core/         models mirroring the API DTOs, and the two HTTP services
└── features/     library, game, book-editor — one folder per screen
```

**Game rules live in the domain.** `GameSession.choose()` applies the consequence, clamps
health, decides whether the player died or reached an ending, and refuses moves once the
game is over. The service layer only loads, delegates and saves. That keeps the rules in one
testable place instead of spread across services.

**Fetch strategies are deliberate, not accidental.** The library listing never loads
sections — it would be wasted work multiplied by every row — and gets chapter counts from
one grouped query instead of a lazy load per book. Loading a book to play fetches sections
and options in two queries rather than one: fetch-joining two collections at once produces a
cross product, and `distinct` on the root entity doesn't undo the duplication inside the
collection. That mistake was made and caught during development; both repositories document
it so it isn't repeated.

**"Saving" isn't a separate operation.** Every choice writes the session, so a saved game is
simply one that's still `PLAYING`.

The brief asks for *"a header allowing the user to stop/pause the game, view the current book
name, their life, and save their progression"*. The name and the life are displayed; the rest
are four controls, each with its own intent:

| Control | What it does |
| :-- | :-- |
| **← Back to Library** | Leaves straight away. The game keeps its place, like closing a book on the table. |
| **💾 Save Progress** | Acknowledges that progress is kept, and stays in the game. Sends no request — the last choice already persisted the session. |
| **⏸ Pause** | Both at once: confirms the game is saved, then steps out. |
| **⏹ Stop** | Ends the adventure for good. The session keeps its history but stops being resumable, so it asks first. |

The distinction that matters is the last one: a reader stepping away expects to come back, so
the only control that can't be undone is the one that asks first.

---

## Notes on the sample data

The four JSON files provided with the brief needed small fixes before they would load, and
the sender confirmed these were fine to make:

- `dragon-quest.json` arrived empty (0 bytes) and was written from scratch.
- All four contained a section `666` typed as `NODE` with no options — invalid under the
  brief's own fourth rule. Where it was reachable it became an `END` (the text reads like a
  bad ending); where nothing pointed at it, it was removed.
- `pirates-jade-sea.json` had an option pointing at section `999`, which doesn't exist —
  corrected to `20`, the section its description clearly means.

The originals are untouched in `books/` at the project root; the fixed copies the
application actually loads are in `backend/src/main/resources/books/`.

The suggested design also shows a synopsis, genre tags and a reading-time estimate on each
card. None of that exists in the source data, so rather than invent it the cards show what
the books actually carry, plus a chapter count derived from the section count.

---

## Known limitations

- **Readers are separated, but not authenticated.** Each game belongs to a `playerId` that the
  browser generates and keeps in local storage, sent on every request as `X-Player-Id`, so one
  reader's "Continue Playing" list shows only their own games. That is a convenience, not a
  boundary: anyone can send any id, the same person on another device is a different player,
  and `/admin` is open to whoever reaches it. The brief doesn't ask for accounts; real ones
  would replace the browser-generated id with one issued at login.
- **`ddl-auto: update` rather than versioned migrations.** Fine when the schema comes from the
  entities and the data is re-seedable, but adding the `player_id` and `version` columns meant
  deleting the local database — which a real deployment could not do. Flyway or Liquibase is
  the answer there.
- **Deleting a book destroys the games played on it**, rather than keeping them as history.
  A saved position means nothing without the sections it refers to, so there is nothing
  sensible to keep — but it does mean a delete is final, which is why the screen asks first.

---

## Also in this repository

`adventure-book-bdd/` (a sibling folder, outside this project) holds a Cucumber suite — 31
scenarios describing the same rules in plain English. It runs **this backend's own domain
classes**, so a change to the game rules that breaks a scenario fails that build. It isn't
part of this submission; the backend's own 100 tests stand on their own.

To run it, install this module first so the suite can resolve it:

```bash
cd backend && mvn install -DskipTests
cd ../../adventure-book-bdd && mvn test
```

That dependency is the reason `spring-boot-maven-plugin` gives its executable jar the `exec`
classifier: repackaging would otherwise replace the plain jar with a fat one whose classes sit
under `BOOT-INF/classes`, where another project can't compile against them. Running the
application is unaffected.
