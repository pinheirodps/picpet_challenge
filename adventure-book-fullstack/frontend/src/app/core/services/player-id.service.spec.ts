import { TestBed } from '@angular/core/testing';
import { PlayerIdService } from './player-id.service';

const STORAGE_KEY = 'adventure-book.player-id';

describe('PlayerIdService', () => {
  beforeEach(() => {
    localStorage.removeItem(STORAGE_KEY);
    TestBed.configureTestingModule({});
  });

  afterEach(() => localStorage.removeItem(STORAGE_KEY));

  it('generates an id and remembers it', () => {
    const id = TestBed.inject(PlayerIdService).current();

    expect(id).toBeTruthy();
    expect(localStorage.getItem(STORAGE_KEY)).toBe(id);
  });

  it('reuses the id already stored, so a reload keeps the same saved games', () => {
    localStorage.setItem(STORAGE_KEY, 'an-existing-reader');

    expect(TestBed.inject(PlayerIdService).current()).toBe('an-existing-reader');
  });

  it('returns the same id every time it is asked', () => {
    const service = TestBed.inject(PlayerIdService);

    expect(service.current()).toBe(service.current());
  });

  // Local storage throws in some privacy modes. The reader should still get their own games
  // for this visit rather than the page failing.
  it('still produces an id when local storage is unavailable', () => {
    spyOn(localStorage, 'getItem').and.throwError('denied');

    const id = TestBed.inject(PlayerIdService).current();

    expect(id).toBeTruthy();
  });
});
