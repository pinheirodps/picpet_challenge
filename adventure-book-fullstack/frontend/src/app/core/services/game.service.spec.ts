import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { GameSession } from '../models/game.model';
import { GameService } from './game.service';

describe('GameService', () => {
  let service: GameService;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiBaseUrl}/games`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(GameService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('starts a game by posting the bookId', () => {
    service.start(1).subscribe();

    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ bookId: 1 });
    req.flush(sampleSession());
  });

  it('sends a choice to the right game', () => {
    service.choose(42, 1).subscribe();

    const req = httpMock.expectOne(`${baseUrl}/42/choices`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ optionIndex: 1 });
    req.flush(sampleSession());
  });

  it('lists saved games', () => {
    service.listSaved().subscribe();

    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('resumes a game by id', () => {
    service.get(42).subscribe();

    const req = httpMock.expectOne(`${baseUrl}/42`);
    expect(req.request.method).toBe('GET');
    req.flush(sampleSession());
  });

  it('stops a game', () => {
    service.stop(7).subscribe();

    const req = httpMock.expectOne(`${baseUrl}/7/stop`);
    expect(req.request.method).toBe('POST');
    req.flush(sampleSession({ status: 'ABANDONED', options: [] }));
  });

  function sampleSession(overrides: Partial<GameSession> = {}): GameSession {
    return {
      gameId: 1,
      bookTitle: 'The Crystal Caverns',
      health: 10,
      maxHealth: 10,
      status: 'PLAYING',
      sectionText: 'You stand at the entrance.',
      options: [{ index: 0, description: 'Go in' }],
      lastConsequence: null,
      ...overrides
    };
  }
});
