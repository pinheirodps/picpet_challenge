import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { BookSummary, Page } from '../models/book.model';
import { BookService } from './book.service';

describe('BookService', () => {
  let service: BookService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(BookService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('sends query and difficulty as request params when provided', () => {
    service.search('caverns', 'EASY', 0).subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiBaseUrl}/books`
    );
    expect(req.request.params.get('query')).toBe('caverns');
    expect(req.request.params.get('difficulty')).toBe('EASY');
    expect(req.request.params.get('page')).toBe('0');
    req.flush({ content: [], page: { size: 12, number: 0, totalElements: 0, totalPages: 0 } });
  });

  it('omits query and difficulty params when blank', () => {
    service.search('', '', 0).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiBaseUrl}/books`);
    expect(req.request.params.has('query')).toBeFalse();
    expect(req.request.params.has('difficulty')).toBeFalse();
    req.flush({ content: [], page: { size: 12, number: 0, totalElements: 0, totalPages: 0 } });
  });

  it('returns the page the backend responds with', (done) => {
    const book: BookSummary = {
      id: 1,
      title: 'The Crystal Caverns',
      author: 'Evelyn Stormrider',
      difficulty: 'EASY',
      sectionCount: 12
    };
    const page: Page<BookSummary> = { content: [book], page: { size: 12, number: 0, totalElements: 1, totalPages: 1 } };

    service.search('', '', 0).subscribe((result) => {
      expect(result).toEqual(page);
      done();
    });

    httpMock.expectOne((r) => r.url === `${environment.apiBaseUrl}/books`).flush(page);
  });

  it('posts the create request to /books', () => {
    service.create({ title: 'New Book', author: 'A', difficulty: 'EASY', sections: [] }).subscribe();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/books`);
    expect(req.request.method).toBe('POST');
    req.flush({ id: 5, title: 'New Book', author: 'A', difficulty: 'EASY', sectionCount: 0 });
  });

  it('gets a single book in full', () => {
    service.get(4).subscribe();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/books/4`);
    expect(req.request.method).toBe('GET');
    req.flush({ id: 4, title: 'A Book', author: 'A', difficulty: 'EASY', gamesInProgress: 0, sections: [] });
  });

  it('puts the revised book to /books/{id}', () => {
    service.update(4, { title: 'Revised', author: 'A', difficulty: 'HARD', sections: [] }).subscribe();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/books/4`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.title).toBe('Revised');
    req.flush({ id: 4, title: 'Revised', author: 'A', difficulty: 'HARD', sectionCount: 0 });
  });

  it('deletes a book', () => {
    service.delete(4).subscribe();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/books/4`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
