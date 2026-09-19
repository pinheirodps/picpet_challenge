import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { PlayerIdService } from '../services/player-id.service';

/** The header the backend reads to tell readers' saved games apart. */
export const PLAYER_ID_HEADER = 'X-Player-Id';

/**
 * Adds this browser's player id to every call to our own API, so the backend can keep one
 * reader's saved games separate from another's.
 *
 * <p>Done here rather than in each service so no future request can forget it. Requests to
 * anywhere other than our API are left untouched — a header naming the reader has no business
 * being sent to a third party.
 */
export const playerIdInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(environment.apiBaseUrl)) {
    return next(req);
  }

  const playerId = inject(PlayerIdService).current();
  return next(req.clone({ setHeaders: { [PLAYER_ID_HEADER]: playerId } }));
};
