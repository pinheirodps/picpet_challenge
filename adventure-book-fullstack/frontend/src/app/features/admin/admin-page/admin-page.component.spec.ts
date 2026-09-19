import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { BookService } from '../../../core/services/book.service';
import { BookSummary, Page } from '../../../core/models/book.model';
import { AdminPageComponent } from './admin-page.component';

describe('AdminPageComponent', () => {
  let fixture: ComponentFixture<AdminPageComponent>;
  let component: AdminPageComponent;
  let bookService: jasmine.SpyObj<BookService>;
  let router: jasmine.SpyObj<Router>;

  const aBook: BookSummary = {
    id: 1,
    title: 'The Crystal Caverns',
    author: 'Evelyn Stormrider',
    difficulty: 'EASY',
    sectionCount: 13
  };

  function pageOf(books: BookSummary[]): Page<BookSummary> {
    return { content: books, page: { size: 100, number: 0, totalElements: books.length, totalPages: 1 } };
  }

  function start() {
    fixture = TestBed.createComponent(AdminPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    bookService = jasmine.createSpyObj<BookService>('BookService', ['search', 'delete']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    bookService.search.and.returnValue(of(pageOf([aBook])));
    bookService.delete.and.returnValue(of(void 0));

    TestBed.configureTestingModule({
      imports: [AdminPageComponent],
      providers: [
        { provide: BookService, useValue: bookService },
        { provide: Router, useValue: router }
      ]
    });
  });

  it('lists every book in the library', () => {
    start();

    expect(component.books()).toEqual([aBook]);
    expect(component.loading()).toBeFalse();
  });

  it('shows a message when the library cannot be loaded', () => {
    bookService.search.and.returnValue(throwError(() => new Error('network error')));

    start();

    expect(component.errorMessage()).toContain('Could not load');
    expect(component.loading()).toBeFalse();
  });

  it('navigates to the empty form to add a book', () => {
    start();

    component.addBook();

    expect(router.navigate).toHaveBeenCalledWith(['/admin/books/new']);
  });

  it('navigates to the edit form for an existing book', () => {
    start();

    component.editBook(7);

    expect(router.navigate).toHaveBeenCalledWith(['/admin/books', 7, 'edit']);
  });

  // Deleting a book also destroys the games played on it, so a misclick is unrecoverable.
  it('asks for confirmation before deleting, and does nothing if refused', () => {
    start();
    spyOn(window, 'confirm').and.returnValue(false);

    component.deleteBook(aBook);

    expect(bookService.delete).not.toHaveBeenCalled();
  });

  it('deletes the book and reloads the list once confirmed', () => {
    start();
    spyOn(window, 'confirm').and.returnValue(true);
    bookService.search.calls.reset();

    component.deleteBook(aBook);

    expect(bookService.delete).toHaveBeenCalledWith(1);
    expect(bookService.search).toHaveBeenCalled();
    expect(component.deletingId()).toBeNull();
  });

  it('reports a failed delete and leaves the list alone', () => {
    start();
    spyOn(window, 'confirm').and.returnValue(true);
    bookService.delete.and.returnValue(throwError(() => new Error('boom')));

    component.deleteBook(aBook);

    expect(component.errorMessage()).toContain('Could not delete');
    expect(component.deletingId()).toBeNull();
  });

  it('goes back to the library', () => {
    start();

    component.backToLibrary();

    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });
});
