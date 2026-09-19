/** The difficulty levels the library filters by, shared by the model and the filter UI. */
export const DIFFICULTIES = ['EASY', 'MEDIUM', 'HARD'] as const;

export type Difficulty = (typeof DIFFICULTIES)[number];

/** Mirrors the backend's BookSummaryDto — what the library listing shows for each book. */
export interface BookSummary {
  id: number;
  title: string;
  author: string;
  difficulty: Difficulty;
  /** How many sections the book has — shown on the card as its chapter count. */
  sectionCount: number;
}

/** Mirrors Spring Data's PagedModel shape (see WebConfig's VIA_DTO page serialization). */
export interface Page<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}
