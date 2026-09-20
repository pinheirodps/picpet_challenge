# Manual test guide — Adventure Book

A walkthrough for checking by hand that the application does everything the assessment asks
for. Every rule in the brief is covered by at least one step below, and each step says what
you should see, so a mismatch is unambiguous.

Allow about 20 minutes. Nothing here needs the automated suites — this is the "would a person
notice something wrong" pass.

---

## Before you start

Two terminals:

```bash
cd backend  && mvn spring-boot:run     # http://localhost:8080
cd frontend && npm start               # http://localhost:4200
```

**Start from a clean library.** Stop the backend, delete `backend/data/`, start it again. The
four sample books reload from `src/main/resources/books/`. Section 8 adds books; section 9
removes them again, but starting clean keeps the counts below exact.

Open http://localhost:4200.

---

## 1. The library loads (Objective 1)

| Check | Expected |
| :-- | :-- |
| The page lists books | 4 cards: The Crystal Caverns, Dragon Quest, Pirates of the Jade Sea, The Prisoner |
| Each card shows author and difficulty | e.g. Evelyn Stormrider — EASY |
| Each card shows a chapter count | "13 chapters" on The Crystal Caverns |
| The heading counts them | "4 Epic Adventures Available" |

**Why this matters:** all four books load, which means all four passed validation at startup.
The seed JSONs shipped with the brief had errors — see the README's "Notes on the sample data"
— and a book that fails validation is not loaded at all, so a missing card here would mean a
book was rejected.

---

## 2. Search and filter (Objective 1)

| Do this | Expected |
| :-- | :-- |
| Type `caverns` in the search box | One card: The Crystal Caverns |
| Clear the box | Back to 4 cards |
| Type `Ashfell` | One card: Dragon Quest — **search matches the author, not just the title** |
| Type `zzzz` | "No adventures match" and no cards |
| Clear, then click **HARD** | Two cards: Dragon Quest and The Prisoner. The button looks pressed |
| Click **HARD** again | Filter clears, 4 cards return |
| Click **HARD**, then type `prisoner` | One card — search and filter combine, they don't replace each other |

Type quickly and watch the list: it should update once you stop, not flicker on every
keystroke. Requests are debounced by 300 ms, and a slower earlier response can't overwrite a
newer one.

---

## 3. Start a game (Objective 2)

Clear any search. On **The Crystal Caverns**, click **Begin Quest**.

| Check | Expected |
| :-- | :-- |
| The story text | "You stand at the entrance of the legendary Crystal Caverns…" |
| Health in the header | 10 / 10 |
| The book title in the header | The Crystal Caverns |
| Choices | Two: "Cross the rope bridge carefully" and "Search the rocky walls for another path" |

The game always opens on the book's single BEGIN section. A book with two beginnings, or
none, can't be saved in the first place — section 8 shows that.

---

## 4. Choices and consequences (Objectives 2 and 3)

Continue from section 1. Pick **"Cross the rope bridge carefully"**.

| Check | Expected |
| :-- | :-- |
| New text | "The bridge creaks under your weight…" |
| Health | Still 10 — this choice carried no consequence |
| No consequence banner | Nothing explaining a health change, because nothing changed |

Now pick **"Try to jump to the other side"**.

| Check | Expected |
| :-- | :-- |
| Health | **3 / 10** (10 − 7) |
| A banner appears | "You land hard and twist your ankle." |
| The banner shows the cost | −7 HP |
| Health looks urgent | At 3 or below the health display changes state |

**This is the important one.** The books ship flavour text explaining every health change, and
the player should read *why* they got hurt, not just watch a number drop.

Now pick **"Continue deeper into the cavern"**.

| Check | Expected |
| :-- | :-- |
| Health | Still 3 |
| The banner is gone | A harmless choice clears the previous explanation — it doesn't linger and mislead |

### The healing ceiling

Start a fresh game of The Crystal Caverns (**Back to library**, then **Begin Quest**). Pick
**"Cross the rope bridge carefully"**, then **"Hold on tightly and crawl across"** — neither
costs anything, so you are still on 10 HP. Now pick **"Rest and recover your strength"**.

| Check | Expected |
| :-- | :-- |
| Banner | "You feel slightly better after resting." (+3 HP) |
| Health | **10 / 10, not 13** — health is capped at the starting maximum |

That's the ceiling working: a heal is applied, but it can't push you past where you began.

To watch a heal that actually moves the number, you need to be hurt first. There is no path
in this book that takes damage and then offers the rest, so use the API instead — or trust
step 8a, where you build a book with a `GAIN_HEALTH` option of your own.

---

## 5. Dying (Objective 3)

Start a fresh game of The Crystal Caverns and take this exact path:

1. "Cross the rope bridge carefully"
2. "Try to jump to the other side" → **3 HP**
3. "Continue deeper into the cavern"
4. **"Swim across"** → −5

| Check | Expected |
| :-- | :-- |
| Health | **0 / 10** — it floors at zero, it never goes negative |
| The game ends | A death message, clearly not a victory |
| The cause is named | "The freezing water chills you to the bone." |
| No choices remain | You cannot keep playing |
| You can leave | A way back to the library |

**Also check the section you died in.** The text should be the *destination* of that last
choice ("You manage to swim across, shivering but alive…"), not the lake shore you chose it
from. A fatal choice still moves you, so you see where the choice led.

That section's text cheerfully says you survived, which reads oddly next to a death screen.
That's the sample book's own writing, not a bug: it was written assuming you'd arrive with
health to spare. The application reports the state correctly — 0 HP, dead — and the death
message plus the consequence text are what tell the player what happened. Worth knowing so
it doesn't look like a defect during a demo.

---

## 6. Reaching an ending (Objective 3)

Start a fresh game and take: "Cross the rope bridge carefully" → "Hold on tightly and crawl
across" → "Follow the path downward" → "Take the boat across the lake" → "Ignore it and
continue forward".

| Check | Expected |
| :-- | :-- |
| The final text | "The hidden passage leads to a giant crystal throne room…" |
| The game ends | The adventure is over and offers no further choices |
| The wording | It says the story **finished** — it does not congratulate you on winning |

**Why:** an END section is not necessarily a good ending. Some are deaths or dead ends, so the
application reports that you reached an ending rather than claiming a victory it can't verify.

---

## 7. Saving, resuming and stopping (Objective 4)

Start a fresh game of **Dragon Quest** and make two choices, noting the section text and your
health. Then click **Back to library** in the header.

| Check | Expected |
| :-- | :-- |
| A "Continue Playing" area appears | It lists Dragon Quest |
| It shows your health | The same number you had |

Click it to resume.

| Check | Expected |
| :-- | :-- |
| The section | Exactly where you stopped — same text |
| Health | Exactly what you had |

**There is no "save" step, and that is deliberate.** Every choice is written to the database
as you make it, so a saved game is just one still in progress. The header's save button
confirms that rather than performing it — pressing it should tell you your progress is already
kept.

### Pressing "Begin Quest" again on the same book

Back in the library, click **Begin Quest** on Dragon Quest — the book you're already part-way
through.

| Check | Expected |
| :-- | :-- |
| Where you land | Back where you stopped, **not** at the opening section |
| Continue Playing | Still one Dragon Quest entry, not two |

Two games on one book would appear as identical rows — same title, often the same health — and
you'd have no way to tell which held your progress. To genuinely play a book twice, stop the
first game and begin again.

### When several games are in progress

Start games on three or four different books, then return to the library.

| Check | Expected |
| :-- | :-- |
| The heading | Shows a count, e.g. "5 in progress" |
| The list | Only the four most recent, on one row |
| Below it | A "Show all 5" button, if there are more than four |
| Pressing it | Every game shows; pressing again collapses back |
| The library | Still visible without scrolling past a wall of saved games |

Now press the header's **stop** control.

| Check | Expected |
| :-- | :-- |
| The game ends | It stops accepting choices |
| Back in the library | Dragon Quest is **no longer** under Continue Playing |

Stopping is a real state change, not just navigation: a stopped game keeps its progress but is
no longer offered as resumable.

### Each reader sees only their own games

Start a game on any book, then open the app in a **private window** (or a different browser).

| Check | Expected |
| :-- | :-- |
| The private window's library | **No** Continue Playing section — that reader has no games |
| Start a game there | It appears only in that window |
| Back in the first window | Still only the game you started there |

Saved games belong to a reader. The browser generates an id on first visit and keeps it in
local storage, sending it with every request; the backend stores it on the session and filters
the resume list by it.

**Say this before anyone asks:** it identifies a reader, it does not authenticate one. The id
is readable and forgeable, and the same person on another device is a different reader. It
exists so two visitors don't share one list, not to protect anything.

### Two choices at once can't lose a turn

Harder to trigger by hand, but worth knowing the behaviour. Open the same game in two tabs,
note the health, then click a costly option in both as fast as you can.

| Check | Expected |
| :-- | :-- |
| One tab | Applies the choice normally |
| The other | Says the game changed elsewhere and to reload — it does not silently apply a second time |
| The health | Reflects one choice, not two, and never a turn that vanished |

Each choice reads the game, changes it and writes it back. Without a guard, both tabs would
read the same health, both subtract, and the second write would overwrite the first — one of
the two turns would disappear. A version column makes the losing write fail instead.

---

## 8. Adding a book, and the validation rules (Objective 5)

From the library, click **Add Your Own Adventure**. That opens **Manage Library** — the one
screen where the catalogue changes. It lists every book with Edit and Delete controls, and a
button to add one.

| Check | Expected |
| :-- | :-- |
| The list | All four sample books, with author, difficulty and chapter count |
| Each row | Has an Edit and a Delete button |
| A note at the top | Warns that revising or removing a book ends games in progress on it |

Click **+ Add Adventure**.

### 8a. A valid book

Fill in a title and author, pick a difficulty, and build three sections:

| Section | Type | Text | Options |
| :-- | :-- | :-- | :-- |
| 1 | BEGIN | You wake in a locked room | "Try the door" → 2 |
| 2 | NODE | The door is stuck fast | "Force it open" → 3, with LOSE_HEALTH 3, text "The frame splinters into your palm" |
| 3 | END | The door gives way and you step outside | *(none)* |

Submit.

| Check | Expected |
| :-- | :-- |
| It's accepted | You return to Manage Library |
| It appears | Your book is listed with 3 chapters |
| The reader sees it | **Back to library**, search for it — it's on the shelf |
| It plays | Begin it, force the door, and you should lose 3 HP with your own text shown |

### 8b. Each rule, one at a time

Now try to submit books that break the rules. **Each one must be rejected, with a message
naming the problem.** Change one thing back after each attempt.

| # | Break this | Expected rejection |
| :-- | :-- | :-- |
| 1 | No BEGIN section (make section 1 a NODE) | Book has no beginning section |
| 2 | Two BEGIN sections | Book has more than one beginning section |
| 3 | No END section (make section 3 a NODE with an option) | Book has no ending section |
| 4 | Point an option at section 99, which doesn't exist | An option points to a non-existent section |
| 5 | A NODE section with no options | A non-ending section has no options |
| 6 | Two sections both numbered 2 | A section number is used twice |

Rules 1–5 are the four from the brief plus the non-ending-needs-options rule. **Rule 6 is an
addition:** the brief doesn't list it, but without it a `gotoId` has no single destination and
the game would route players unpredictably.

**Break several at once** — say, no beginning *and* a section with no options. The response
should list **every** reason, not stop at the first. Someone fixing a book shouldn't have to
resubmit six times to discover six problems.

---

## 9. Revising and removing a book

Not asked for by the brief, but without it a book published with a mistake is stuck in the
library forever. Go to **Manage Library** and find the book you added in section 8.

### 9a. Revising

Click **Edit**.

| Check | Expected |
| :-- | :-- |
| The heading | Says *Revise Adventure*, not *Add* |
| Every field is filled in | Title, author, difficulty, **and the text of every section** |
| Options came back too | Section 2 still has "Force it open" → 3 |
| So did the consequence | LOSE_HEALTH, 3, with your wording |
| The button | Says *Save Changes* |

Change the title and rewrite the text of section 1. Save.

| Check | Expected |
| :-- | :-- |
| It's accepted | Back to Manage Library, with the new title in the row |
| The reader sees it | **Back to library**, search, begin it — your rewritten opening shows |

**Keep the section numbers the same when you do this.** Renumbering is the easy case;
reusing the same numbers is the one that used to fail, because the old sections had to be
deleted before the new ones could take their numbers.

### 9b. A revision is held to the same rules

Edit the book again and break it — change the END section to a NODE, so the book has no
ending at all. Save.

| Check | Expected |
| :-- | :-- |
| It's rejected | "Book has no ending section", on the form |
| You stay on the form | Your work isn't thrown away |
| The stored book is untouched | Go back, begin it — it still plays as it did |

### 9c. Editing a book someone is playing

Begin your book from the library, make one choice, then **Back to Library** — it's now under
Continue Playing. Go to Manage Library and click **Edit** on it.

| Check | Expected |
| :-- | :-- |
| A warning at the top | Says 1 game is in progress and saving will end it |
| On save | A confirmation asks first |
| If you cancel | Nothing is saved |
| If you accept | The change goes through, and that game is no longer under Continue Playing |

**Why the game ends:** the reader's saved place is a section number in the version they
started. Once the sections change, that number may point somewhere else entirely — resuming
into a rewritten story silently is worse than being told the book changed.

### 9d. Removing

Back on Manage Library, click **Delete** on your book.

| Check | Expected |
| :-- | :-- |
| It asks first | The confirmation names the book, and says games on it go too |
| If you cancel | The book stays |
| If you accept | The row disappears |
| The reader | **Back to library**, search for it — "No adventures match" |
| Its games | Any saved game on it is gone from Continue Playing, not left broken |

---

## 10. The API directly (optional)

Swagger UI: **http://localhost:8080/swagger-ui.html** — every endpoint with its request and
response shapes, and the error format.

Worth a look:

```bash
# The library, filtered
curl "http://localhost:8080/api/books?query=caverns&difficulty=EASY"

# A book that doesn't exist — a clean 404, not a stack trace
curl -i http://localhost:8080/api/books/9999

# A rejected book lists every problem at once
curl -i -X POST http://localhost:8080/api/books \
  -H "Content-Type: application/json" \
  -d '{"title":"Broken","author":"Nobody","difficulty":"EASY","sections":[{"id":1,"text":"x","type":"NODE"}]}'
```

The last one returns 400 with several messages: no beginning, no ending, and a non-ending
section without options.

Bad requests should blame the caller, never the server:

```bash
# No body at all, and truncated JSON — both 400, not 500
curl -i -X POST http://localhost:8080/api/games -H "Content-Type: application/json"
curl -i -X POST http://localhost:8080/api/games -H "Content-Type: application/json" -d '{"bookId":'

# A body that parses but is missing a required field — 400 naming the field
curl -i -X POST http://localhost:8080/api/games -H "Content-Type: application/json" -d '{}'
```

The first two should say the body is missing or not valid JSON; the third should say
`bookId: must not be null`. A 500 here would be wrong — the request never arrived in a
readable state, so there is nothing broken on the server to report.

The whole lifecycle of a book, from the command line:

```bash
# Create one, and note the id it comes back with
curl -X POST http://localhost:8080/api/books -H "Content-Type: application/json" \
  -d '{"title":"CLI Book","author":"Me","difficulty":"EASY","sections":[
       {"id":1,"text":"Start","type":"BEGIN","options":[{"description":"On","gotoId":2}]},
       {"id":2,"text":"End","type":"END"}]}'

# Read it back in full — sections, options and consequences, plus games in progress
curl http://localhost:8080/api/books/<id>

# Revise it, keeping the same section numbers
curl -X PUT http://localhost:8080/api/books/<id> -H "Content-Type: application/json" \
  -d '{"title":"CLI Book v2","author":"Me","difficulty":"HARD","sections":[
       {"id":1,"text":"A new start","type":"BEGIN","options":[{"description":"On","gotoId":2}]},
       {"id":2,"text":"A new end","type":"END"}]}'

# And remove it — 204, then 404 on the next read
curl -i -X DELETE http://localhost:8080/api/books/<id>
```

---

## What "passing" means

Every check above behaves as described, and in particular:

- All four sample books load and are playable.
- A health change is always explained by the text the book ships.
- Death floors at zero and is never described as a win.
- Reaching an ending says *finished*, not *won*.
- Progress survives leaving and coming back, with no save button needed.
- Every validation rule is enforced, and a bad book is told everything that's wrong with it.
- A book can be corrected after publishing, and a revision is held to the same rules.
- Nothing destructive happens without being asked first, and the question says what it costs.
- One reader's saved games are their own, not everyone's.
- A turn can't be lost to two choices racing each other.

If something doesn't match, note the step number — each maps to an automated test, so the
matching spec in `backend/src/test/`, `frontend/src/app/**/*.spec.ts` or `frontend/e2e/` is
the place to look.
