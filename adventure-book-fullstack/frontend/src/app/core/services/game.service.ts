import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { GameSession, SavedGame } from '../models/game.model';

/** Talks to the backend's /api/games endpoints — starting, playing, and resuming a game. */
@Injectable({ providedIn: 'root' })
export class GameService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/games`;

  start(bookId: number): Observable<GameSession> {
    return this.http.post<GameSession>(this.baseUrl, { bookId });
  }

  get(gameId: number): Observable<GameSession> {
    return this.http.get<GameSession>(`${this.baseUrl}/${gameId}`);
  }

  choose(gameId: number, optionIndex: number): Observable<GameSession> {
    return this.http.post<GameSession>(`${this.baseUrl}/${gameId}/choices`, { optionIndex });
  }

  /** Deliberately ends a game — the header's stop control. */
  stop(gameId: number): Observable<GameSession> {
    return this.http.post<GameSession>(`${this.baseUrl}/${gameId}/stop`, {});
  }

  listSaved(): Observable<SavedGame[]> {
    return this.http.get<SavedGame[]>(this.baseUrl);
  }
}
