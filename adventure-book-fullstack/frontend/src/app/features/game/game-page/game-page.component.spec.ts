import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of, throwError, Subject } from 'rxjs';
import { GameService } from '../../../core/services/game.service';
import { GameSession } from '../../../core/models/game.model';
import { GamePageComponent } from './game-page.component';

describe('GamePageComponent', () => {
  let fixture: ComponentFixture<GamePageComponent>;
  let component: GamePageComponent;
  let gameService: jasmine.SpyObj<GameService>;
  let router: jasmine.SpyObj<Router>;

  function playingSession(overrides: Partial<GameSession> = {}): GameSession {
    return {
      gameId: 1,
      bookTitle: 'The Crystal Caverns',
      health: 10,
      maxHealth: 10,
      status: 'PLAYING',
      sectionText: 'You stand at the entrance.',
      options: [
        { index: 0, description: 'Cross the bridge' },
        { index: 1, description: 'Search the walls' }
      ],
      lastConsequence: null,
      ...overrides
    };
  }

  function setUp(params: { bookId?: string; gameId?: string } = { bookId: '1' }) {
    gameService = jasmine.createSpyObj<GameService>('GameService', ['start', 'choose', 'get', 'stop']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);

    TestBed.configureTestingModule({
      imports: [GamePageComponent],
      providers: [
        { provide: GameService, useValue: gameService },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(params) } } }
      ]
    });

    fixture = TestBed.createComponent(GamePageComponent);
    component = fixture.componentInstance;
  }

  it('starts a game for the book id in the route on init', () => {
    setUp({ bookId: '1' });
    gameService.start.and.returnValue(of(playingSession()));

    fixture.detectChanges();

    expect(gameService.start).toHaveBeenCalledWith(1);
    expect(component.session()?.bookTitle).toBe('The Crystal Caverns');
    expect(component.loading()).toBeFalse();
  });

  it('shows an error and no session when starting fails', () => {
    setUp({ bookId: '1' });
    gameService.start.and.returnValue(throwError(() => new Error('boom')));

    fixture.detectChanges();

    expect(component.session()).toBeNull();
    expect(component.errorMessage()).toContain('Could not load');
  });

  it('resumes an existing game by id instead of starting a new one', () => {
    setUp({ gameId: '42' });
    gameService.get.and.returnValue(of(playingSession({ gameId: 42, sectionText: 'Where you left off.' })));

    fixture.detectChanges();

    expect(gameService.get).toHaveBeenCalledWith(42);
    expect(gameService.start).not.toHaveBeenCalled();
    expect(component.session()?.sectionText).toBe('Where you left off.');
  });

  it('sends the chosen option index and updates the session', () => {
    setUp({ bookId: '1' });
    gameService.start.and.returnValue(of(playingSession()));
    gameService.choose.and.returnValue(of(playingSession({ sectionText: 'The bridge creaks.' })));
    fixture.detectChanges();

    component.choose(0);

    expect(gameService.choose).toHaveBeenCalledWith(1, 0);
    expect(component.session()?.sectionText).toBe('The bridge creaks.');
  });

  it('ignores further choices while one is in flight', () => {
    setUp({ bookId: '1' });
    gameService.start.and.returnValue(of(playingSession()));
    fixture.detectChanges();
    const inFlight$ = new Subject<GameSession>();
    gameService.choose.and.returnValue(inFlight$);

    component.choose(0);
    component.choose(1);

    expect(gameService.choose).toHaveBeenCalledTimes(1);
  });

  it('surfaces what the last choice did to the player', () => {
    setUp({ bookId: '1' });
    gameService.start.and.returnValue(of(playingSession()));
    gameService.choose.and.returnValue(
      of(
        playingSession({
          health: 6,
          lastConsequence: { type: 'LOSE_HEALTH', healthChange: -4, text: 'You scrape your shoulder.' }
        })
      )
    );
    fixture.detectChanges();

    component.choose(0);
    fixture.detectChanges();

    expect(component.session()?.lastConsequence?.healthChange).toBe(-4);
    expect(fixture.nativeElement.textContent).toContain('You scrape your shoulder.');
    expect(fixture.nativeElement.textContent).toContain('-4 HP');
  });

  it('renders the death screen with the consequence that killed the player', () => {
    setUp({ bookId: '1' });
    gameService.start.and.returnValue(
      of(
        playingSession({
          status: 'DEAD',
          health: 0,
          options: [],
          lastConsequence: { type: 'LOSE_HEALTH', healthChange: -10, text: 'The trap closes.' }
        })
      )
    );

    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('You Have Perished');
    expect(text).toContain('The trap closes.');
  });

  it('renders a neutral ending rather than declaring a win', () => {
    setUp({ bookId: '1' });
    gameService.start.and.returnValue(of(playingSession({ status: 'FINISHED', options: [] })));

    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('The End');
  });

  it('stops the game through the API when the stop control is used', () => {
    setUp({ bookId: '1' });
    gameService.start.and.returnValue(of(playingSession()));
    gameService.stop.and.returnValue(of(playingSession({ status: 'ABANDONED', options: [] })));
    fixture.detectChanges();

    component.stopGame();
    fixture.detectChanges();

    expect(gameService.stop).toHaveBeenCalledWith(1);
    expect(component.session()?.status).toBe('ABANDONED');
    expect(fixture.nativeElement.textContent).toContain('Adventure Stopped');
  });

  it('navigates home when going back to the library', () => {
    setUp({ bookId: '1' });
    gameService.start.and.returnValue(of(playingSession()));
    fixture.detectChanges();

    component.backToLibrary();

    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });
});
