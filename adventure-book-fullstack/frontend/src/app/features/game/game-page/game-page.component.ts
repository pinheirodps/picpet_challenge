import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable } from 'rxjs';
import { GameService } from '../../../core/services/game.service';
import { GameSession } from '../../../core/models/game.model';

/** How long the "Saved ✓" acknowledgement stays on the button. */
const SAVED_NOTICE_MS = 2000;

/** How long "Saved ✓" shows before pausing takes the reader back to the library. */
const PAUSE_NOTICE_MS = 700;

/**
 * The game screen (Objectives 2 and 3): shows the current section's text and options, lets
 * the player make a choice, and reflects health and status changes as they happen. The same
 * component serves a brand-new game (/play/:bookId) and a resumed one (/play/resume/:gameId).
 *
 * <p>The header carries what the brief asks for — a way to stop or pause the game, the book's
 * name, the player's life, and saving their progression. The name and the life are displayed;
 * the rest are four controls, each with its own intent:
 *
 * <ul>
 *   <li><strong>Back to Library</strong> leaves straight away. The game keeps its place, like
 *       closing a book on the table.</li>
 *   <li><strong>Save Progress</strong> acknowledges that the progress is kept, and stays in
 *       the game. It sends no request: the backend persisted the session on the last choice,
 *       so there is nothing left to save.</li>
 *   <li><strong>Pause</strong> is the two together — it confirms the game is saved and then
 *       steps out, for a reader who wants to be told their place is kept before leaving.</li>
 *   <li><strong>Stop</strong> ends the adventure for good. The session keeps its history but
 *       is no longer resumable, so it asks first.</li>
 * </ul>
 */
@Component({
  selector: 'app-game-page',
  standalone: true,
  templateUrl: './game-page.component.html',
  styleUrl: './game-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class GamePageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly gameService = inject(GameService);
  private readonly destroyRef = inject(DestroyRef);

  readonly session = signal<GameSession | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly busy = signal(false);
  readonly justSaved = signal(false);

  private savedNoticeTimer?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    const params = this.route.snapshot.paramMap;
    const gameId = params.get('gameId');
    const source$ = gameId
      ? this.gameService.get(Number(gameId))
      : this.gameService.start(Number(params.get('bookId')));

    this.destroyRef.onDestroy(() => clearTimeout(this.savedNoticeTimer));

    source$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (session) => {
        this.session.set(session);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not load this adventure. Please go back and try again.');
        this.loading.set(false);
      }
    });
  }

  choose(optionIndex: number): void {
    const current = this.session();
    if (!current || this.busy()) {
      return;
    }
    this.run(this.gameService.choose(current.gameId, optionIndex), 'That choice could not be made. Please try again.');
  }

  /**
   * Ends the adventure for good. Asks first, because the game stops being resumable and there
   * is no undo — a reader who meant to step away wants {@link #saveAndExit} instead.
   */
  stopGame(): void {
    const current = this.session();
    if (!current || this.busy()) {
      return;
    }
    if (!confirm('End this adventure? Your progress is kept, but you will not be able to resume it.')) {
      return;
    }
    this.run(this.gameService.stop(current.gameId), 'The game could not be stopped. Please try again.');
  }

  /**
   * Confirms the reader's progress is safe, without leaving the game.
   *
   * <p>No request is needed — the backend persisted the session on the last choice — so this
   * acknowledges that and stays put. Leaving is what the other two controls are for: "Back to
   * Library" steps away with the game still resumable, and "Stop" ends it for good.
   */
  saveProgress(): void {
    clearTimeout(this.savedNoticeTimer);
    this.justSaved.set(true);
    this.savedNoticeTimer = setTimeout(() => this.justSaved.set(false), SAVED_NOTICE_MS);
  }

  /**
   * Confirms the game is saved, then leaves — the brief's "pause".
   *
   * <p>Like {@link #saveProgress} it sends no request, because the session is already stored.
   * The brief pause before navigating is the point: the reader sees their place was kept
   * rather than being moved away the instant they click.
   */
  pauseGame(): void {
    clearTimeout(this.savedNoticeTimer);
    this.justSaved.set(true);
    this.savedNoticeTimer = setTimeout(() => this.router.navigate(['/']), PAUSE_NOTICE_MS);
  }

  backToLibrary(): void {
    this.router.navigate(['/']);
  }

  private run(request$: Observable<GameSession>, failureMessage: string): void {
    this.busy.set(true);
    this.errorMessage.set(null);
    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (session) => {
        this.session.set(session);
        this.busy.set(false);
      },
      error: () => {
        this.errorMessage.set(failureMessage);
        this.busy.set(false);
      }
    });
  }
}
