# Presentation guide

How the application was built, in the order it was built, and why each decision went the way
it did. Written to be talked through — each step says what existed before it, what it added,
and what it cost.

The brief asks that the objectives be treated in order, and they were. Each one was working
and tested before the next began, which is why the application was demonstrable at every
stage rather than only at the end.

---

## Before objective 1: the foundation

Three things had to exist before a library page could list anything.

**The domain model.** `Book → Section → Option → Consequence`. The shape comes straight from
the sample JSON files, with one deliberate difference: a section's `id` from the JSON is
stored as `sectionNumber`, separate from the JPA primary key. Section numbers are only unique
*within* a book, so they can't be the primary key — two books both having a section 1 is
normal.

> **If asked "why not use the JSON id directly?"** Because the same number repeats across
> books. Making it the primary key means either a composite key or a collision. Keeping them
> separate also means `gotoId` references stay readable as the book's own numbering.

**The validation rules.** Five classes, one per rule, behind a sealed `ValidationRule`
interface. Sealed because adding a rule is a deliberate change to what "valid" means — not
something another package should be able to slip in by implementing an interface.

Four rules come from the brief; the fifth is an addition:

| Rule | Source |
| :-- | :-- |
| Exactly one BEGIN | Brief |
| At least one END | Brief |
| Every `gotoId` points at a section that exists | Brief |
| Non-ending sections have options | Brief |
| Section numbers are unique within a book | **Added** |

> **On the fifth rule:** without it, `findBySectionNumber` has no single answer. Two sections
> numbered 7 means a `gotoId: 7` routes players unpredictably — the game would appear to work
> and quietly send different readers to different places.

**Loading the sample books.** `BookLoaderService` reads the JSONs at startup and runs them
through the same validator a submitted book goes through. That reuse is the thing to point
at: there is one definition of "valid", not one for seed data and another for user input.

### The sample data needed fixing first

All four provided books failed validation. Worth mentioning early, because it demonstrates
the validator working rather than being an inconvenience:

- `dragon-quest.json` arrived empty — 0 bytes.
- All four had a section `666` typed as `NODE` with no options, which breaks the brief's own
  fourth rule.
- `pirates-jade-sea.json` pointed an option at section `999`, which doesn't exist.

The sender was asked and replied: *"Please ignore the errors if you found some, and if
necessary you can do the changes you think are needed."* So they were fixed — section 666
became an `END` where it was reachable and was removed where nothing pointed at it, `999`
became `20`, and dragon-quest was written from scratch. Originals are untouched in `books/`
at the project root; the fixed copies the app loads are in `backend/src/main/resources/books/`.

> **An earlier plan was to leave two books failing**, as proof the validator worked. That was
> dropped: a library that ships half-broken books demos badly, and the integration test proves
> the validator just as well.

---

## Objective 1 — the library

**Backend.** `GET /api/books` with free-text search, difficulty filter and pagination, built
on Spring Data `Specification` so the two filters compose instead of needing four query
methods.

**The N+1 decision.** Each card shows a chapter count. The obvious implementation —
`book.getSections().size()` per row — is one query per book. Instead, one grouped count query
over the whole page:

```java
@Query("select s.book.id, count(s) from Section s where s.book.id in :bookIds group by s.book.id")
List<Object[]> countSectionsByBookIds(@Param("bookIds") Collection<Long> bookIds);
```

The listing never loads sections at all.

**Frontend.** Standalone Angular components, signals for state, `OnPush` throughout. Search,
filter and pagination feed **one** RxJS pipeline rather than three, debounced 300 ms, with
`switchMap` so a slow response for `"ca"` can't land after the one for `"caverns"` and
overwrite the grid.

> **Point to show if you demo one technical thing on the frontend:** type quickly in the
> search box. One request goes out, not one per keystroke, and the results can't arrive out
> of order.

### What broke here

**Search was completely broken at first**, and the unit tests passed anyway. The pipeline had
`distinctUntilChanged()` on a `Subject<void>` — comparing `undefined` to `undefined`, so every
emission after the first was swallowed. The first search worked and nothing after it did.

The fix was signals plus `switchMap`. The lesson worth stating: the tests mocked the service
and asserted it was called, which is true even when the pipeline is dead after one use. The
E2E test that now guards this types three different searches in a row.

---

## Objective 2 — playing a game

**The design decision that matters most.** Game rules live in the domain, not in a service:

```java
public void choose(int optionIndex) { ... }   // on GameSession
```

Moving between sections, applying a consequence, and deciding whether the game ended are all
operations on one object's own state. `GameService` loads, delegates and saves — it holds no
rules. This is GRASP's Information Expert, and it is why `GameSessionTest` is the largest test
class in the project (14 tests) without needing any Spring context.

> **If asked "why not put this in the service?"** Because then the rules spread across
> whatever calls them, and testing them means standing up a service with mocked repositories.
> Here they're testable with `new`.

**The fetch strategy.** Loading a book to play needs sections *and* options. The natural
single query is wrong:

```java
// Wrong: produces a cartesian product
left join fetch b.sections s left join fetch s.options
```

Two parallel collection joins multiply each other — a 12-section book came back with 21
sections, each duplicated. `distinct` on the root entity does **not** dedupe the nested
collection. So: two queries in the same persistence context, which Hibernate merges.

> **This bug had real symptoms:** `beginnings()` returned 2 instead of 1, so games refused to
> start with "Book must have exactly one beginning". Both repositories now document it, and
> `BookRepositoryIntegrationTest` is the regression guard. The same mistake existed in
> `GameSessionRepository` and was found by reviewing for it deliberately.

---

## Objective 3 — consequences, health, endings

Added to objective 2's turn loop: health, the consequence text, death at zero, and endings.

**Two naming decisions worth defending:**

**`FINISHED`, not `WON`.** An END section can be a bad ending — "the trap closes" is an
ending too. The status records that the reader reached one, rather than claiming a victory
the application can't verify.

**A fatal choice still moves the reader.** Death is decided *after* the move, so the screen
shows the room they died in rather than the one they walked out of:

```java
currentSectionNumber = chosen.getGotoId();     // move first
settleOutcome();                               // then decide what that move meant

// ...

private void settleOutcome() {
    if (health <= MIN_HEALTH) {
        status = GameStatus.DEAD;
    } else if (currentSection().isEnding()) {
        status = GameStatus.FINISHED;
    }
}
```

> **If asked why that isn't a Strategy** — a fair question, given the validation rules are:
> there are two outcomes, fixed by the rules of the game, and they aren't independent. Death
> takes precedence over reaching an ending, and the `else if` says so in two lines. Behind an
> interface, that precedence would move into the order of a list in another file — hiding the
> very thing the method exists to state. A third outcome that varied on its own (a timer, a
> status effect) would be where that trade-off flips, and the javadoc says so.

**The consequence text.** The books ship flavour text for every health change — *"You land
hard and twist your ankle."* An early version applied the health change but dropped the text,
so the player watched the number fall from 10 to 3 with no explanation. `lastConsequence` is
kept on the session so the UI can say why.

> **Demo suggestion:** take the death path in The Crystal Caverns (bridge → jump → continue →
> swim). It shows the consequence banner, the health going 10 → 3 → 0, the banner clearing
> on a harmless choice, and the death naming its cause.
>
> One honest caveat: the section you die in says *"shivering but alive"*. That's the sample
> book's own writing, not a bug — worth saying before someone asks.

---

## Objective 4 — saving progress

**This objective needed almost no new code, which is the interesting part.**

Every choice already persisted the session — that's what playing *is*. So a saved game is
simply one whose status is still `PLAYING`, and "save" is not a separate operation or a
separate entity.

What was added: `GET /api/games` listing resumable games, and a "Continue Playing" area.

### The header, and why it has three controls

The brief asks for *"a header allowing the user to stop/pause the game, view the current book
name, their life, and save their progression"*. The name and the life are displayed; the rest are four controls:

| Control | What it does |
| :-- | :-- |
| ← Back to Library | Leaves straight away; the game keeps its place. |
| 💾 Save Progress | Confirms progress is kept, and stays in the game. Sends no request. |
| ⏸ Pause | Both at once — confirms the save, then steps out. |
| ⏹ Stop | Ends the adventure for good, and asks first. |

> **If asked why there's a Stop when Figure 1 doesn't show one:** the brief's text asks for it
> ("stop/pause the game") even though the mockup omits it, along with the life display it also
> asks for. The text was treated as the requirement and the figure as a sketch.

> **And why Stop is separate from Pause:** "stop/pause" can mean step away or give up. Pause
> covers the first; Stop covers the second and can't be undone, which is why it's the one that
> asks for confirmation.

> **If asked "where's the save endpoint?"** There isn't one, deliberately. Adding a separate
> save action would mean progress could be lost by not pressing it. The reader can close the
> tab mid-sentence and lose nothing.

### Two refinements that came from actually using it

Both of these were invisible until the app was used with real accumulated state, which is
worth saying — they're the kind of thing only a running application surfaces.

**Starting a game on a book already in progress now resumes it.** `POST /api/games` used to
create a session every time, so pressing "Begin Quest" twice left two games on one book. In
the resume list they're indistinguishable — same title, often the same health — and the reader
can't tell which holds their progress. Now the endpoint hands back the existing game. Playing
a book twice at once is still possible: stop the first, then begin again.

**"Continue Playing" collapses to the four most recent.** Uncapped, it pushed the library —
the point of the page — off the screen entirely. It now shows four, with the total in the
heading and a "Show all" toggle. Resuming almost always means the game just put down, so four
covers the real case.

> **This changed the E2E suite in an instructive way.** Eight tests failed after the resume
> change, all of them assuming "Begin Quest" always starts fresh. That assumption was never
> stated, just relied upon. The fix was a helper that stops any leftover game first, which
> made each test's precondition explicit instead of accidental.

### Two more, once the shared state became obvious

**Saved games belong to a reader.** `GET /api/games` used to return every game in progress
from everyone, so one visitor's "Continue Playing" list showed another's game — and resuming
it took the game over. Sessions now carry a `playerId`, generated by the browser and sent as
`X-Player-Id`. It identifies a reader without authenticating one; the limitations section says
what that does and doesn't buy.

**Two choices can't overwrite each other.** Every choice is a read-modify-write, so the same
game open in two tabs would read health 10 twice, subtract twice, and quietly lose a turn.
`GameSession` carries a `@Version` column, and the losing write comes back as 409 telling the
caller to reload.

> Optimistic rather than pessimistic locking: a game belongs to one reader making one choice
> at a time, so conflicts are rare. Locking the row on every read would charge every request
> to protect a case that almost never happens.
>
> **Verified:** two `POST /choices` fired in parallel return 200 and 409, not 200 and 200.

---

## Objective 5 — adding books

The form submits and displays whatever the backend rejected. It deliberately does **not**
re-implement the book rules — no beginning/ending/`gotoId` checks in TypeScript.

> **Why that matters:** two implementations of the same rules drift. The backend is the one
> that decides, and it lists *every* reason at once rather than stopping at the first, so
> someone fixing a book doesn't resubmit six times to discover six problems.

The frontend only checks that required fields are filled in before letting submit through.

### What broke here

**The editor rendered a blank page.** `newSection()` read the `sections` getter — which
delegates to `this.form.get(...)` — *during* `this.form`'s own construction. A `TypeError`
that silently killed Angular's bootstrap, with no console error pointing at the cause. Fixed
by passing `nextId` as a parameter instead.

---

## Beyond the brief: revising and removing books

Not asked for. Added because publishing a book with a mistake otherwise leaves it in the
library permanently, which makes objective 5 much less useful than it looks.

**Why a separate `/admin` screen.** The library is the reader's shelf — the suggested design
shows it as somewhere to pick an adventure. A delete control one click from "Begin Quest"
serves neither job. Everything that changes the catalogue lives in one place, including the
add button, which is the honest separation: reading and maintaining are different roles with
different risks.

**Both operations end games in progress on that book, and warn first.** A reader's saved
position is a section number in the version they started. Once the sections change, that
number may point somewhere else entirely — silently resuming into a rewritten story is worse
than being told the book changed.

**A revision is validated by the same rules as a new book**, so editing can't sneak an
unplayable book past them.

### What broke here

**Editing a book while keeping the same section numbers returned 500.** The most common edit
of all — fixing a typo without renumbering. Hibernate issued the `INSERT`s for the new
sections before the `DELETE`s for the old ones, and the unique `(book_id, section_number)`
constraint fired. Fixed by splitting the operation and flushing between the two phases.

**Loading a book for editing dropped every section's text.** Caught by a unit test written to
assert the loaded form was valid, before any manual testing found it.

---

## Testing

| Suite | Count | What it covers |
| :-- | :-- | :-- |
| Backend | 100 | Unit, slice, and one full-application integration test |
| Frontend unit | 76 | Components and services, Karma + Jasmine |
| Playwright E2E | 43 | Real browser against the real API, no mocks |
| Cucumber BDD | 31 scenarios | The same rules in plain English, separate project |

**The full-application test is the one to mention.** Everything else mocks at least one seam.
`ApplicationIntegrationTest` runs real context, real seed loading, real validation wiring,
real HTTP — it's what catches a book that stops loading or a rule Spring doesn't pick up.

**The BDD project is a separate exercise**, not part of the submission. Worth mentioning only
if there's time. Its interesting property: it originally had its own copy of the domain, drifted
three times while passing 100%, and now depends on the real backend jar so a rule change that
breaks a scenario fails that build.

> **If asked about the Karma setup:** browsers can't be downloaded on this machine, so
> `CHROME_BIN` points at the Chromium that Playwright already had on disk, and Playwright
> drives the installed Edge.

---

## Known limitations — say these before being asked

**Readers are separated, but not authenticated.** Each game belongs to a `playerId` the
browser generates and keeps in local storage, sent as `X-Player-Id` on every request, so a
reader's "Continue Playing" list holds only their own games.

That is a convenience, not a boundary. Anyone can send any id; the same person on another
device is a different player; `/admin` is open to whoever reaches it. Say this plainly — the
failure mode of this pattern is someone mistaking it for security. Real accounts would replace
the browser-generated id with one issued at login, which the rest of the code is already
shaped for.

**`ddl-auto: update`.** Fine when the schema comes from the entities and the data is
re-seedable; a real deployment would use Flyway or Liquibase.

**Deleting a book destroys the games played on it.** A saved position means nothing without
the sections it refers to, so there's nothing sensible to keep — but it does make a delete
final, which is why it asks first.

**The mockup's synopsis, genre tags and reading time were not added.** That data doesn't
exist in the provided JSONs. Inventing it would have meant showing readers fabricated
information about real books. The chapter count *was* added, because it's derivable from
data that genuinely exists.

---

## If you have five minutes, demo this

**First:** stop the backend, delete `backend/data/`, start it again. The four sample books
reload and the library is exactly as an assessor would first see it. Running the E2E suite
leaves published books behind, and a shelf full of `The Whispering Woods 1789822718297`
undercuts the demo.

1. **Library** — search "Ashfell", show it matches the author, not just titles.
2. **Play The Crystal Caverns** — bridge, jump (−7, banner explains why), continue (banner
   clears), swim (death, cause named).
3. **Back to library** — the game is under Continue Playing; resume it, same place, same
   health. No save button was pressed.
4. **Manage Library** — edit a book, show every field comes back filled in, break it (turn
   the END into a NODE) and show the backend refusing with its reason.
5. **Swagger** — `/swagger-ui.html`, every endpoint documented with its error shapes.
