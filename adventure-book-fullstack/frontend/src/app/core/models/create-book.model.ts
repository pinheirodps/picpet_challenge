export type SectionType = 'BEGIN' | 'NODE' | 'END';
export type ConsequenceType = 'LOSE_HEALTH' | 'GAIN_HEALTH';

/** Mirrors the backend's ConsequenceRequest. */
export interface ConsequenceRequest {
  type: ConsequenceType;
  value: number;
  text: string;
}

/** Mirrors the backend's OptionRequest. */
export interface OptionRequest {
  description: string;
  gotoId: number;
  consequence: ConsequenceRequest | null;
}

/** Mirrors the backend's SectionRequest. */
export interface SectionRequest {
  id: number;
  text: string;
  type: SectionType;
  options: OptionRequest[];
}

/** Mirrors the backend's CreateBookRequest — the payload for Objective 5. */
export interface CreateBookRequest {
  title: string;
  author: string;
  difficulty: string;
  sections: SectionRequest[];
}

/**
 * Mirrors the backend's BookDetailDto — a whole book, read back for editing.
 *
 * <p>Its sections deliberately share `SectionRequest`'s shape, so the editor can load a book,
 * change it, and submit it straight back as a `CreateBookRequest` with no translation step
 * that could quietly drop a field.
 */
export interface BookDetail {
  id: number;
  title: string;
  author: string;
  difficulty: string;
  /** How many games are still in progress — revising or deleting will end them. */
  gamesInProgress: number;
  sections: SectionRequest[];
}
