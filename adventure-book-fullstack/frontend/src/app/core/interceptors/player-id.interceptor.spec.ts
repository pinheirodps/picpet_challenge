import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { PlayerIdService } from '../services/player-id.service';
import { PLAYER_ID_HEADER, playerIdInterceptor } from './player-id.interceptor';

describe('playerIdInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([playerIdInterceptor])),
        provideHttpClientTesting(),
        { provide: PlayerIdService, useValue: { current: () => 'reader-a' } }
      ]
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('adds the player id to calls to our API', () => {
    http.get(`${environment.apiBaseUrl}/games`).subscribe();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/games`);
    expect(req.request.headers.get(PLAYER_ID_HEADER)).toBe('reader-a');
    req.flush([]);
  });

  // A header naming the reader has no business being sent anywhere else.
  it('leaves requests to other hosts untouched', () => {
    http.get('https://example.com/something').subscribe();

    const req = httpMock.expectOne('https://example.com/something');
    expect(req.request.headers.has(PLAYER_ID_HEADER)).toBeFalse();
    req.flush({});
  });
});
