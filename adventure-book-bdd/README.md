# Adventure Book — BDD suite

Standalone Cucumber project that describes the Adventure Book business rules in plain
language and checks them automatically. It lives outside the main `adventure-book-fullstack`
Maven project on purpose — it doesn't need the real backend to exist, and it won't be part
of the graded submission.

## Why this exists

The assessment spec reads almost like Gherkin already: "a book is invalid if... a non-ending
section has no options", "once health reaches zero, the player dies". This project turns those
sentences into runnable scenarios.

It started with its own small domain model, written before the backend existed. That model has
since been deleted: the scenarios now run the **real backend's** `Book`, `Section`, `Option`,
`GameSession` and `BookValidator`. The `.feature` files were unchanged by that swap, which is
the point — they describe behavior, not implementation.

**Why the swap was worth doing.** Two models meant two sets of rules, and nothing kept them
honest. When the backend renamed `WON` to `FINISHED`, added `ABANDONED`, and fixed a fatal
choice to still move the reader, this project carried on passing at 100% while describing
rules the application no longer had. A green suite that tests the wrong thing is worse than a
failing one. Wiring it to the real classes immediately surfaced a third drift nobody had
noticed: a scenario expecting the message `"Section id 7 is used by 2 sections"` when the
application actually says `"Section number 7..."`.

## What's covered

- **`book_validation.feature`** — the five rules that make a book valid or invalid: exactly
  one beginning, at least one ending, every option pointing at a real section, every
  non-ending section having at least one option, and every section number being unique within
  the book (this last one isn't in the original spec, but the real backend needed it once
  section lookup by number had to be unambiguous). Plus one scenario that checks no rule the
  application ships has been left out of the ones above.
- **`game_navigation.feature`** — starting a game, moving between sections by picking
  options, reaching an ending, and stopping a game part way through (Objective 2).
- **`game_consequences.feature`** — health going up or down, the sentence that explains why
  it moved, dying at zero health, and neither a dead nor a stopped player being able to keep
  playing (Objective 3).
- **`game_progress.feature`** — a game is saved automatically on every choice (there's no
  separate save step, matching the real backend), a finished or stopped game drops out of the
  saved games list, and a saved game can be resumed with its section and health intact
  (Objective 4, extra).

Two details are worth calling out, because they're where the backend was corrected and these
scenarios now guard against sliding back — really guard, since they run the shipped code:

- **`FINISHED`, not `WON`.** An END section can be a bad ending — "the trap closes" is an
  ending too — so the status records that the reader reached one rather than claiming a win.
- **A fatal choice still moves the reader.** Death is decided after the move, so the screen
  shows the room they died in instead of the one they walked out of.

## Running it

Requires Java 21 and Maven. The backend has to be installed into the local Maven repository
first, since this project compiles against it:

```
cd ../adventure-book-fullstack/backend && mvn install -DskipTests
cd ../../adventure-book-bdd && mvn test
```

Cucumber prints each scenario as it runs, followed by a summary. All 31 scenarios (140 steps)
should pass, in about two seconds — nothing here starts a database or a Spring context.

## Project layout

```
src/test/java/com/pictet/adventurebook/bdd/
├── steps/     Cucumber step definitions, calling the real backend classes
├── support/   the two pieces of scaffolding the backend can't provide here
└── runner/    RunCucumberTest — the single entry point `mvn test` runs

src/test/resources/features/   the .feature files themselves, in plain English
```

There is no `domain/` package any more. The game rules come from the backend jar.

`support/` holds what's left:

- **`InMemoryGameStore`** stands in for the JPA repository, so save-and-resume scenarios run
  without a database. It fakes the storage, not the rules — the games inside it are real
  `GameSession` objects.
- **`Validators`** builds the backend's `BookValidator` by hand, because there's no Spring
  context here to discover the rules. That hand-written list could silently fall behind, so a
  scenario asserts it against `ValidationRule`'s permitted subclasses — the interface is
  sealed, so the compiler knows the full set. Add a sixth rule to the backend and that
  scenario fails until it's wired in here too.

## A note on the table format

Feature files build a book from a table of sections. A plain option is just the id of the
section it leads to:

```
| id | type  | options |
| 1  | BEGIN | 2       |
```

An option that carries a health consequence is written as
`description|gotoId|TYPE|value`, so the common case (no consequence) stays easy to read:

```
| id | type  | options                        |
| 1  | BEGIN | Jump the gap\|2\|LOSE_HEALTH\|4 |
```

A fifth field sets the sentence the reader is shown when the health changes. Leave it out and
the option's own description is used, so only the scenarios that assert on the wording have to
spell it out:

```
| id | type  | options                                                      |
| 1  | BEGIN | Jump the gap\|2\|LOSE_HEALTH\|4\|You scrape your leg on the rock |
```
