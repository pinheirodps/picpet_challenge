import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable } from 'rxjs';
import { GameService } from '../../../core/services/game.service';
import { GameSession } from '../../../core/models/game.model';

/**
 * The game screen (Objectives 2 and 3): shows the current section's text and options, lets
 * the player make a choice, and reflects health and status changes as they happen. The same
 * component serves a brand-new game (/play/:bookId) and a resumed one (/play/resume/:gameId).
 *
 * <p>There is no "save" request: the backend persists the session on every choice, so the
 * header's save control confirms that rather than performing it. Stopping, on the other
 * hand, is a real state change and does call the API.
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

  stopGame(): void {
    const current = this.session();
    if (!current || this.busy()) {
      return;
    }
    this.run(this.gameService.stop(current.gameId), 'The game could not be stopped. Please try again.');
  }

  /** Confirms what the backend already did on the last choice — see the class javadoc. */
  confirmSave(): void {
    clearTimeout(this.savedNoticeTimer);
    this.justSaved.set(true);
    this.savedNoticeTimer = setTimeout(() => this.justSaved.set(false), 2000);
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
