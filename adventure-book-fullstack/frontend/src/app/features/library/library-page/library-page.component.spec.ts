import { ComponentFixture, TestBed, fakeAsync, flush, flushMicrotasks, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { BookService } from '../../../core/services/book.service';
import { GameService } from '../../../core/services/game.service';
import { Page, BookSummary } from '../../../core/models/book.model';
import { SavedGame } from '../../../core/models/game.model';
import { LibraryPageComponent } from './library-page.component';

describe('LibraryPageComponent', () => {
  let fixture: ComponentFixture<LibraryPageComponent>;
  let component: LibraryPageComponent;
  let bookService: jasmine.SpyObj<BookService>;
  let gameService: jasmine.SpyObj<GameService>;
  let router: jasmine.SpyObj<Router>;

  const DEBOUNCE = 300;

  function pageOf(books: BookSummary[], totalPages = 1): Page<BookSummary> {
    return {
      content: books,
      page: { size: 12, number: 0, totalElements: books.length, totalPages }
    };
  }

  /** `count` saved games, newest first, as the API returns them. */
  function manySavedGames(count: number): SavedGame[] {
    return Array.from({ length: count }, (_, i) => ({
      gameId: i + 1,
      bookTitle: `Book ${i + 1}`,
      health: 10,
      updatedAt: '2026-01-01T00:00:00Z'
    }));
  }

  const aBook: BookSummary = {
    id: 1,
    title: 'The Crystal Caverns',
    author: 'Evelyn Stormrider',
    difficulty: 'EASY',
    sectionCount: 12
  };

  /** Creates the component and lets its initial search settle. */
  function start() {
    fixture = TestBed.createComponent(LibraryPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    settle();
  }

  /**
   * Advances past the debounce and re-renders. The re-render matters: `toSignal` only keeps
   * its subscription alive while something reads the signal, and in the real app that reader
   * is the template — so the test has to render too, or the pipeline goes quiet.
   */
  function settle() {
    // toObservable pushes signal changes through an effect, which runs as a microtask —
    // so the value has to be flushed into the pipeline before the debounce timer matters.
    fixture.detectChanges();
    flushMicrotasks();
    tick(DEBOUNCE);
    fixture.detectChanges();
  }

  beforeEach(() => {
    bookService = jasmine.createSpyObj<BookService>('BookService', ['search']);
    gameService = jasmine.createSpyObj<GameService>('GameService', ['listSaved']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    bookService.search.and.returnValue(of(pageOf([aBook])));
    gameService.listSaved.and.returnValue(of([]));

    TestBed.configureTestingModule({
      imports: [LibraryPageComponent],
      providers: [
        { provide: BookService, useValue: bookService },
        { provide: GameService, useValue: gameService },
        { provide: Router, useValue: router }
      ]
    });
  });

  it('loads books on start', fakeAsync(() => {
    start();

    expect(bookService.search).toHaveBeenCalledWith('', '', 0, 12);
    expect(component.books()).toEqual([aBook]);
    expect(component.loading()).toBeFalse();
    flush();
  }));

  it('shows an error message when the search fails', fakeAsync(() => {
    bookService.search.and.returnValue(throwError(() => new Error('network error')));

    start();

    expect(component.errorMessage()).toContain('Could not load');
    expect(component.loading()).toBeFalse();
    flush();
  }));

  // The search pipeline used to swallow every keystroke after the first, because
  // distinctUntilChanged sat on a Subject<void> and compared undefined to undefined.
  it('issues a fresh search for each distinct query, not just the first', fakeAsync(() => {
    start();
    bookService.search.calls.reset();

    component.onQueryChange('caverns');
    settle();
    component.onQueryChange('dragon');
    settle();

    expect(bookService.search).toHaveBeenCalledTimes(2);
    expect(bookService.search.calls.argsFor(0)[0]).toBe('caverns');
    expect(bookService.search.calls.argsFor(1)[0]).toBe('dragon');
    flush();
  }));

  it('collapses rapid typing into a single search', fakeAsync(() => {
    start();
    bookService.search.calls.reset();

    component.onQueryChange('c');
    tick(100);
    fixture.detectChanges();
    component.onQueryChange('ca');
    tick(100);
    fixture.detectChanges();
    component.onQueryChange('cav');
    settle();

    expect(bookService.search).toHaveBeenCalledTimes(1);
    expect(bookService.search.calls.mostRecent().args[0]).toBe('cav');
    flush();
  }));

  it('toggling the same difficulty twice clears the filter', fakeAsync(() => {
    start();

    component.onDifficultyToggle('EASY');
    expect(component.selectedDifficulty()).toBe('EASY');
    settle();

    component.onDifficultyToggle('EASY');
    expect(component.selectedDifficulty()).toBe('');
    settle();
    flush();
  }));

  it('searches with the chosen difficulty', fakeAsync(() => {
    start();
    bookService.search.calls.reset();

    component.onDifficultyToggle('HARD');
    settle();

    expect(bookService.search).toHaveBeenCalledWith('', 'HARD', 0, 12);
    flush();
  }));

  it('resets to the first page when the filter changes', fakeAsync(() => {
    start();
    component.goToPage(2);
    settle();

    component.onDifficultyToggle('HARD');
    settle();

    expect(component.pageIndex()).toBe(0);
    flush();
  }));

  it('requests the page the reader navigated to', fakeAsync(() => {
    start();
    bookService.search.calls.reset();

    component.goToPage(1);
    settle();

    expect(bookService.search).toHaveBeenCalledWith('', '', 1, 12);
    flush();
  }));

  it('exposes the page count so the template can hide pagination when it fits', fakeAsync(() => {
    bookService.search.and.returnValue(of(pageOf([aBook], 3)));

    start();

    expect(component.totalPages()).toBe(3);
    flush();
  }));

  it('loads saved games on start', fakeAsync(() => {
    const saved: SavedGame[] = [
      { gameId: 1, bookTitle: 'The Crystal Caverns', health: 6, updatedAt: '2026-01-01T00:00:00Z' }
    ];
    gameService.listSaved.and.returnValue(of(saved));

    start();

    expect(component.savedGames()).toEqual(saved);
    flush();
  }));

  // An uncapped list pushes the library — the point of the page — off the screen, so only
  // the most recent few show until the reader asks for the rest.
  it('shows only the four most recent saved games at first', fakeAsync(() => {
    gameService.listSaved.and.returnValue(of(manySavedGames(9)));

    start();

    expect(component.savedGames().length).toBe(9);
    expect(component.visibleSavedGames().length).toBe(4);
    expect(component.hiddenSavedGameCount()).toBe(5);
    flush();
  }));

  it('shows every saved game once expanded, and collapses again', fakeAsync(() => {
    gameService.listSaved.and.returnValue(of(manySavedGames(9)));
    start();

    component.toggleAllSavedGames();
    expect(component.visibleSavedGames().length).toBe(9);

    component.toggleAllSavedGames();
    expect(component.visibleSavedGames().length).toBe(4);
    flush();
  }));

  it('offers no expand control when everything already fits', fakeAsync(() => {
    gameService.listSaved.and.returnValue(of(manySavedGames(3)));

    start();

    expect(component.visibleSavedGames().length).toBe(3);
    expect(component.hiddenSavedGameCount()).toBe(0);
    flush();
  }));

  it('clears saved games instead of blocking the page when the fetch fails', fakeAsync(() => {
    gameService.listSaved.and.returnValue(throwError(() => new Error('network error')));

    start();

    expect(component.savedGames()).toEqual([]);
    expect(component.errorMessage()).toBeNull();
    flush();
  }));

  it('navigates to the play route when a quest begins', fakeAsync(() => {
    start();

    component.onBeginQuest(7);

    expect(router.navigate).toHaveBeenCalledWith(['/play', 7]);
    flush();
  }));

  it('navigates to the resume route when a saved game is picked', fakeAsync(() => {
    start();

    component.resumeGame(3);

    expect(router.navigate).toHaveBeenCalledWith(['/play/resume', 3]);
    flush();
  }));

  it('sends the reader to the management screen to add an adventure', fakeAsync(() => {
    start();

    component.onManageLibrary();

    expect(router.navigate).toHaveBeenCalledWith(['/admin']);
    flush();
  }));
});
