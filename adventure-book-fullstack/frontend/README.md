# Adventure Book — frontend

Angular 19 client for the Adventure Book API. See the [project README](../README.md) for
the full picture, including how to start the backend this depends on.

## Running

```bash
npm install
npm start          # http://localhost:4200
```

The backend must be running on http://localhost:8080 — the dev server does not proxy, it
calls the API directly, and the backend allows that origin through CORS.

```bash
npm run build          # production bundle into dist/
npm test               # 73 unit tests (Karma + Jasmine)
npm run test:coverage  # the same, with a coverage report in coverage/
npm run e2e            # 39 Playwright tests against the running app
npm run e2e:headed     # the same, with a visible browser
```

Playwright drives the machine's installed Edge, so there's no browser to download. If Karma
can't find a browser, set `CHROME_BIN` to a Chrome or Chromium executable.

`npm run build` swaps `environment.ts` for `environment.prod.ts`, which points the API at a
relative `/api` — that assumes the bundle is served behind the same host as the backend.
Change that file if the two are deployed separately.

## Layout

```
src/app/
├── core/
│   ├── models/     TypeScript mirrors of the API's DTOs
│   └── services/   BookService and GameService — thin HTTP wrappers, no logic
└── features/
    ├── library/     the home page and its book cards
    ├── game/        the play screen
    ├── admin/       managing the library — add, revise, remove
    └── book-editor/ the book form, used for both adding and revising
```

The reader's screens and the maintainer's screen are kept apart. The library is a shelf to
pick from; everything that changes the catalogue lives under `/admin`, including adding a
book. `/create` redirects there so old links still work.

Components are standalone, state is held in signals, and change detection is `OnPush`
throughout. Search and filter share one RxJS pipeline (debounced, with `switchMap` so a
slow response can't overwrite a newer one); everything else is a plain request per action.

Validation is the backend's job. The editor checks that required fields are filled in, then
submits and displays whatever the server rejected — it deliberately does not re-implement
the book rules.

## Tests

Unit specs sit next to the code they cover. The `e2e/` folder holds the Playwright suite,
which drives a real browser against the real API:

| File | Covers |
| :-- | :-- |
| `library.spec.ts` | Objective 1 — listing, search by title and author, difficulty filter, empty state |
| `play-game.spec.ts` | Objectives 2 and 3 — choices, consequence text, health, endings, dying |
| `save-and-resume.spec.ts` | Objective 4 — automatic saving, resuming mid-book, stopping |
| `create-book.spec.ts` | Objective 5 — publishing a book, playing it, and each way it can be rejected |
| `manage-books.spec.ts` | Beyond the brief — revising a book, rejecting a bad revision, deleting one, and what happens to games in progress |

Both servers must be running. `manage-books` deletes everything it creates; `create-book`
leaves its books behind on purpose, since publishing is the thing it tests. Delete
`backend/data/` first for a run against exactly the four samples.
