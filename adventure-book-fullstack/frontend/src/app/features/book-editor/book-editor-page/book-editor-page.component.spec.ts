import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { BookService } from '../../../core/services/book.service';
import { BookDetail } from '../../../core/models/create-book.model';
import { BookEditorPageComponent } from './book-editor-page.component';

describe('BookEditorPageComponent', () => {
  let fixture: ComponentFixture<BookEditorPageComponent>;
  let component: BookEditorPageComponent;
  let bookService: jasmine.SpyObj<BookService>;
  let router: jasmine.SpyObj<Router>;

  /** The route parameter the component reads to decide create-vs-edit. */
  let bookIdParam: string | null = null;

  const savedBook: BookDetail = {
    id: 4,
    title: 'The Crystal Caverns',
    author: 'Evelyn Stormrider',
    difficulty: 'MEDIUM',
    gamesInProgress: 0,
    sections: [
      {
        id: 1,
        text: 'You stand at the entrance',
        type: 'BEGIN',
        options: [
          { description: 'Go in', gotoId: 2, consequence: { type: 'LOSE_HEALTH', value: 4, text: 'A scrape' } }
        ]
      },
      { id: 2, text: 'You made it out', type: 'END', options: [] }
    ]
  };

  /** Creates the component; pass a book id to put it in edit mode. */
  function start(bookId?: number) {
    bookIdParam = bookId === undefined ? null : String(bookId);
    fixture = TestBed.createComponent(BookEditorPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    bookIdParam = null;
    bookService = jasmine.createSpyObj<BookService>('BookService', ['create', 'get', 'update']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    bookService.get.and.returnValue(of(savedBook));

    TestBed.configureTestingModule({
      imports: [BookEditorPageComponent],
      providers: [
        { provide: BookService, useValue: bookService },
        { provide: Router, useValue: router },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => bookIdParam } } }
        }
      ]
    });
  });

  it('starts with one BEGIN section that has one option', () => {
    start();
    expect(component.sections.length).toBe(1);
    expect(component.sections.at(0).get('type')?.value).toBe('BEGIN');
    expect(component.optionsOf(0).length).toBe(1);
  });

  it('does not submit when the form is invalid', () => {
    start();
    component.form.get('title')?.setValue('');

    component.submit();

    expect(bookService.create).not.toHaveBeenCalled();
    expect(component.form.get('title')?.touched).toBeTrue();
  });

  it('adds and removes sections', () => {
    start();
    component.addSection();
    expect(component.sections.length).toBe(2);

    component.removeSection(1);
    expect(component.sections.length).toBe(1);
  });

  it('adds and removes options within a section', () => {
    start();
    component.addOption(0);
    expect(component.optionsOf(0).length).toBe(2);

    component.removeOption(0, 1);
    expect(component.optionsOf(0).length).toBe(1);
  });

  it('toggles a consequence group on and off an option', () => {
    start();
    expect(component.hasConsequence(0, 0)).toBeFalse();

    component.toggleConsequence(0, 0);
    expect(component.hasConsequence(0, 0)).toBeTrue();

    component.toggleConsequence(0, 0);
    expect(component.hasConsequence(0, 0)).toBeFalse();
  });

  it('submits the form value and navigates home on success', () => {
    start();
    fillValidForm(component);
    bookService.create.and.returnValue(
      of({ id: 1, title: 'x', author: 'y', difficulty: 'EASY' as const, sectionCount: 1 })
    );

    component.submit();

    expect(bookService.create).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/admin']);
  });

  it('shows the backend validation errors instead of navigating away on failure', () => {
    start();
    fillValidForm(component);
    bookService.create.and.returnValue(
      throwError(() => ({ error: { messages: ['Book has no ending section'] } }))
    );

    component.submit();

    expect(component.submitErrors()).toEqual(['Book has no ending section']);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  function fillValidForm(cmp: BookEditorPageComponent): void {
    cmp.form.get('title')?.setValue('New Book');
    cmp.sections.at(0).get('text')?.setValue('Start of the story');
    cmp.optionsOf(0).at(0).get('description')?.setValue('Go on');
    cmp.optionsOf(0).at(0).get('gotoId')?.setValue(2);
  }

  describe('revising an existing book', () => {
    it('loads the book into the form, options and consequences included', () => {
      start(4);

      expect(bookService.get).toHaveBeenCalledWith(4);
      expect(component.editingId()).toBe(4);
      expect(component.form.get('title')?.value).toBe('The Crystal Caverns');
      expect(component.form.get('difficulty')?.value).toBe('MEDIUM');

      expect(component.sections.length).toBe(2);
      expect(component.sections.at(0).get('id')?.value).toBe(1);
      expect(component.optionsOf(0).length).toBe(1);
      expect(component.hasConsequence(0, 0)).toBeTrue();
      expect(component.optionsOf(0).at(0).get('consequence')?.value).toEqual({
        type: 'LOSE_HEALTH',
        value: 4,
        text: 'A scrape'
      });
    });

    // An END section carries no options; loading one must not leave an empty option behind
    // from the blank starting form.
    it('keeps an ending section free of options', () => {
      start(4);

      expect(component.sections.at(1).get('type')?.value).toBe('END');
      expect(component.optionsOf(1).length).toBe(0);
    });

    it('loads into a form that is valid as it stands', () => {
      start(4);

      // A book that came from the backend already passed validation, so nothing the editor
      // requires should be missing — if this fails, the load dropped a field.
      const invalid: string[] = [];
      Object.entries((component.form.controls as Record<string, { invalid: boolean }>)).forEach(
        ([name, control]) => {
          if (control.invalid) {
            invalid.push(name);
          }
        }
      );
      expect(invalid).toEqual([]);
      expect(component.form.valid).toBeTrue();
    });

    it('submits an update rather than creating a second book', () => {
      start(4);
      bookService.update.and.returnValue(
        of({ id: 4, title: 'x', author: 'y', difficulty: 'EASY' as const, sectionCount: 2 })
      );

      component.submit();

      expect(bookService.update).toHaveBeenCalled();
      expect(bookService.update.calls.mostRecent().args[0]).toBe(4);
      expect(bookService.create).not.toHaveBeenCalled();
      expect(router.navigate).toHaveBeenCalledWith(['/admin']);
    });

    it('reports a book that could not be loaded', () => {
      bookService.get.and.returnValue(throwError(() => new Error('gone')));

      start(4);

      expect(component.submitErrors()[0]).toContain('Could not load');
      expect(component.loading()).toBeFalse();
    });

    // A revision ends games that are part-way through, so it asks first.
    it('asks before ending games in progress, and does not save if refused', () => {
      bookService.get.and.returnValue(of({ ...savedBook, gamesInProgress: 2 }));
      start(4);
      spyOn(window, 'confirm').and.returnValue(false);

      component.submit();

      expect(component.gamesAtRisk()).toBe(2);
      expect(bookService.update).not.toHaveBeenCalled();
    });

    it('saves once the reader accepts that games will end', () => {
      bookService.get.and.returnValue(of({ ...savedBook, gamesInProgress: 2 }));
      start(4);
      spyOn(window, 'confirm').and.returnValue(true);
      bookService.update.and.returnValue(
        of({ id: 4, title: 'x', author: 'y', difficulty: 'EASY' as const, sectionCount: 2 })
      );

      component.submit();

      expect(bookService.update).toHaveBeenCalled();
    });

    it('does not ask when no game is in progress', () => {
      start(4);
      const confirmSpy = spyOn(window, 'confirm');
      bookService.update.and.returnValue(
        of({ id: 4, title: 'x', author: 'y', difficulty: 'EASY' as const, sectionCount: 2 })
      );

      component.submit();

      expect(confirmSpy).not.toHaveBeenCalled();
      expect(bookService.update).toHaveBeenCalled();
    });
  });
});
