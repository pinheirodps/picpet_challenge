import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';
import { BookService } from '../../../core/services/book.service';
import { GameService } from '../../../core/services/game.service';
import { BookSummary, Difficulty, DIFFICULTIES } from '../../../core/models/book.model';
import { SavedGame } from '../../../core/models/game.model';
import { BookCardComponent } from '../book-card/book-card.component';

const PAGE_SIZE = 12;

/** How many saved games the "Continue Playing" row shows before it has to be expanded. */
const COLLAPSED_SAVED_GAMES = 4;

/** What the library is showing right now, or why it can't. */
interface LibraryState {
  books: BookSummary[];
  totalBooks: number;
  totalPages: number;
  loading: boolean;
  failed: boolean;
}

const LOADING: LibraryState = { books: [], totalBooks: 0, totalPages: 0, loading: true, failed: false };

/**
 * The home page (Objective 1): lists every book, with free-text search, a difficulty
 * filter and pagination, plus a "continue playing" shortcut for games still in progress
 * (Objective 4).
 *
 * <p>All three inputs feed one pipeline, built in a field initializer because that's where
 * {@code toObservable} has an injection context. The whole thing is debounced: typing needs
 * it, and 300ms on a filter click is imperceptible, which is a fair trade for not having two
 * competing pipelines. {@code switchMap} cancels a superseded request — without it a slow
 * response for "ca" could land after the one for "caverns" and overwrite the grid.
 */
@Component({
  selector: 'app-library-page',
  standalone: true,
  imports: [BookCardComponent],
  templateUrl: './library-page.component.html',
  styleUrl: './library-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LibraryPageComponent {
  private readonly bookService = inject(BookService);
  private readonly gameService = inject(GameService);
  private readonly router = inject(Router);

  readonly difficulties = DIFFICULTIES;

  readonly query = signal('');
  readonly selectedDifficulty = signal<Difficulty | ''>('');
  readonly pageIndex = signal(0);
  readonly savedGames = signal<SavedGame[]>([]);

  private readonly criteria = computed(() => ({
    query: this.query(),
    difficulty: this.selectedDifficulty(),
    page: this.pageIndex()
  }));

  private readonly state = toSignal(
    toObservable(this.criteria).pipe(
      debounceTime(300),
      distinctUntilChanged(
        (a, b) => a.query === b.query && a.difficulty === b.difficulty && a.page === b.page
      ),
      switchMap(({ query, difficulty, page }) =>
        this.bookService.search(query, difficulty, page, PAGE_SIZE).pipe(
          map((result) => ({
            books: result.content,
            totalBooks: result.page.totalElements,
            totalPages: result.page.totalPages,
            loading: false,
            failed: false
          })),
          startWith(LOADING),
          catchError(() =>
            of({ books: [], totalBooks: 0, totalPages: 0, loading: false, failed: true })
          )
        )
      )
    ),
    { initialValue: LOADING }
  );

  /**
   * Whether the reader has asked to see every saved game rather than the most recent few.
   *
   * <p>The list is collapsed by default because resuming almost always means the game just
   * put down, and an uncapped list of them pushes the library — the point of the page — off
   * the screen. Someone who plays a dozen books shouldn't have to scroll past all of them to
   * reach the shelf.
   */
  readonly showAllSavedGames = signal(false);

  /** The saved games actually rendered: the most recent few, or all of them once expanded. */
  readonly visibleSavedGames = computed(() =>
    this.showAllSavedGames() ? this.savedGames() : this.savedGames().slice(0, COLLAPSED_SAVED_GAMES)
  );

  readonly hiddenSavedGameCount = computed(() =>
    Math.max(0, this.savedGames().length - COLLAPSED_SAVED_GAMES)
  );

  readonly books = computed(() => this.state().books);
  readonly totalBooks = computed(() => this.state().totalBooks);
  readonly totalPages = computed(() => this.state().totalPages);
  readonly loading = computed(() => this.state().loading);
  readonly errorMessage = computed(() =>
    this.state().failed ? 'Could not load the library right now. Please try again.' : null
  );

  constructor() {
    // A failed fetch here shouldn't block the rest of the page — resuming is a bonus, not
    // something the library depends on to be usable.
    this.gameService.listSaved().subscribe({
      next: (games) => this.savedGames.set(games),
      error: () => this.savedGames.set([])
    });
  }

  onQueryChange(value: string): void {
    this.pageIndex.set(0);
    this.query.set(value);
  }

  onDifficultyToggle(difficulty: Difficulty): void {
    this.pageIndex.set(0);
    this.selectedDifficulty.update((current) => (current === difficulty ? '' : difficulty));
  }

  goToPage(page: number): void {
    this.pageIndex.set(page);
  }

  toggleAllSavedGames(): void {
    this.showAllSavedGames.update((shown) => !shown);
  }

  resumeGame(gameId: number): void {
    this.router.navigate(['/play/resume', gameId]);
  }

  onBeginQuest(bookId: number): void {
    this.router.navigate(['/play', bookId]);
  }

  /**
   * Goes to the management screen, where adding, revising and removing books all live.
   * Adding used to open the form directly; it now goes through the same screen as the other
   * two, so there's one place that changes the catalogue.
   */
  onManageLibrary(): void {
    this.router.navigate(['/admin']);
  }
}
