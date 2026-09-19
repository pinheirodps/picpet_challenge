import { ConsequenceType } from './create-book.model';

export type GameStatus = 'PLAYING' | 'DEAD' | 'FINISHED' | 'ABANDONED';

/** Mirrors the backend's OptionDto. */
export interface GameOption {
  index: number;
  description: string;
}

/**
 * Mirrors the backend's ConsequenceDto — what the player's last choice did to them, so
 * the UI can explain a health change instead of just showing a smaller number.
 */
export interface Consequence {
  type: ConsequenceType;
  /** Signed: negative for damage, positive for healing. */
  healthChange: number;
  text: string | null;
}

/** Mirrors the backend's GameSessionDto — the full state needed to render one turn. */
export interface GameSession {
  gameId: number;
  bookTitle: string;
  health: number;
  maxHealth: number;
  status: GameStatus;
  sectionText: string;
  options: GameOption[];
  lastConsequence: Consequence | null;
}

/** Mirrors the backend's SavedGameDto — one entry in the "resume a game" list. */
export interface SavedGame {
  gameId: number;
  bookTitle: string;
  health: number;
  updatedAt: string;
}
