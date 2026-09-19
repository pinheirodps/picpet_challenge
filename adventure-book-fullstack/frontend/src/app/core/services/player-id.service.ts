import { Injectable } from '@angular/core';

const STORAGE_KEY = 'adventure-book.player-id';

/**
 * Gives this browser a stable id, so the reader's saved games are theirs rather than shared
 * with every other visitor.
 *
 * <p><strong>This identifies a reader; it does not authenticate one.</strong> The id is
 * generated here and kept in local storage — anyone can read it, change it, or send a
 * different one straight to the API. It exists so that two people using the app don't see
 * each other's games in "Continue Playing"; it is not a security boundary, and the backend
 * documents the same caveat.
 *
 * <p>Because it lives in this browser's local storage, the same person on a different device,
 * or after clearing site data, is a different player and starts with an empty list. That is
 * the trade-off of not having accounts.
 */
@Injectable({ providedIn: 'root' })
export class PlayerIdService {
  private readonly id = this.loadOrCreate();

  /** The id to send with each request. Stable for as long as this browser keeps its storage. */
  current(): string {
    return this.id;
  }

  private loadOrCreate(): string {
    // Local storage throws in some privacy modes, so a failure here falls back to an id that
    // lives only as long as the page — the reader still gets their own games for this visit.
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored) {
        return stored;
      }

      const created = newId();
      localStorage.setItem(STORAGE_KEY, created);
      return created;
    } catch {
      return newId();
    }
  }
}

function newId(): string {
  // randomUUID needs a secure context, which http://localhost counts as — but a deployment
  // served over plain http elsewhere would not, so there's a fallback.
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `player-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}
