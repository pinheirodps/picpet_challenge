import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { BookService } from '../../../core/services/book.service';
import { ApiErrorResponse } from '../../../core/models/error-response.model';
import {
  BookDetail,
  ConsequenceType,
  CreateBookRequest,
  SectionRequest,
  SectionType
} from '../../../core/models/create-book.model';
import { DIFFICULTIES } from '../../../core/models/book.model';

const SECTION_TYPES: SectionType[] = ['BEGIN', 'NODE', 'END'];
const CONSEQUENCE_TYPES: ConsequenceType[] = ['LOSE_HEALTH', 'GAIN_HEALTH'];

/**
 * The book form, used both to add a book (Objective 5, extra) and to revise an existing one.
 * Which mode it is in comes from the route: a `bookId` parameter means edit, its absence
 * means create.
 *
 * <p>It submits straight to the backend, which validates with the exact same BookValidator
 * the built-in library uses — this form does no structural validation of its own (no
 * beginning/ending/gotoId checks), it only makes sure required fields are filled in before
 * letting the submit go through, and then shows whatever the backend rejected. One form for
 * both modes means a revised book is held to exactly the same rules as a new one.
 */
@Component({
  selector: 'app-book-editor-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './book-editor-page.component.html',
  styleUrl: './book-editor-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BookEditorPageComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly bookService = inject(BookService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly sectionTypes = SECTION_TYPES;
  readonly consequenceTypes = CONSEQUENCE_TYPES;
  readonly difficulties = DIFFICULTIES;

  readonly submitting = signal(false);
  readonly submitErrors = signal<string[]>([]);
  readonly showFieldErrors = signal(false);

  /** The book being revised, or null when adding a new one. */
  readonly editingId = signal<number | null>(null);
  readonly loading = signal(false);
  /** Games that will be ended by saving this revision — shown as a warning. */
  readonly gamesAtRisk = signal(0);

  readonly form: FormGroup = this.fb.group({
    title: ['', Validators.required],
    author: [''],
    difficulty: ['EASY'],
    sections: this.fb.array([this.newSection('BEGIN')])
  });

  ngOnInit(): void {
    const bookId = Number(this.route.snapshot.paramMap.get('bookId'));
    if (!bookId) {
      return;
    }

    this.editingId.set(bookId);
    this.loading.set(true);
    this.bookService
      .get(bookId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (book) => {
          this.fillFrom(book);
          this.loading.set(false);
        },
        error: () => {
          this.submitErrors.set(['Could not load that book. It may have been removed.']);
          this.loading.set(false);
        }
      });
  }

  /** Rebuilds the form from a loaded book, replacing the empty starting section. */
  private fillFrom(book: BookDetail): void {
    this.gamesAtRisk.set(book.gamesInProgress);
    this.form.patchValue({
      title: book.title,
      author: book.author ?? '',
      difficulty: book.difficulty ?? 'EASY'
    });

    this.sections.clear();
    book.sections.forEach((section) => this.sections.push(this.sectionFrom(section)));
  }

  get sections(): FormArray {
    return this.form.get('sections') as FormArray;
  }

  optionsOf(sectionIndex: number): FormArray {
    return this.sections.at(sectionIndex).get('options') as FormArray;
  }

  addSection(): void {
    this.sections.push(this.newSection('NODE', this.sections.length + 1));
  }

  removeSection(index: number): void {
    this.sections.removeAt(index);
  }

  addOption(sectionIndex: number): void {
    this.optionsOf(sectionIndex).push(this.newOption());
  }

  removeOption(sectionIndex: number, optionIndex: number): void {
    this.optionsOf(sectionIndex).removeAt(optionIndex);
  }

  toggleConsequence(sectionIndex: number, optionIndex: number): void {
    const option = this.optionsOf(sectionIndex).at(optionIndex) as FormGroup;
    if (option.get('consequence')) {
      option.removeControl('consequence');
    } else {
      option.addControl(
        'consequence',
        this.fb.group({
          type: ['LOSE_HEALTH', Validators.required],
          value: [1, [Validators.required, Validators.min(1)]],
          text: ['']
        })
      );
    }
  }

  hasConsequence(sectionIndex: number, optionIndex: number): boolean {
    return !!this.optionsOf(sectionIndex).at(optionIndex).get('consequence');
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.showFieldErrors.set(true);
      this.submitErrors.set(['Some required fields are still empty — they are highlighted below.']);
      return;
    }

    const bookId = this.editingId();
    if (bookId && this.gamesAtRisk() > 0 && !this.confirmEndingGames()) {
      return;
    }

    this.submitting.set(true);
    this.submitErrors.set([]);

    const request = this.form.value as CreateBookRequest;
    const save$ = bookId ? this.bookService.update(bookId, request) : this.bookService.create(request);

    save$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.router.navigate(['/admin']),
      error: (err) => {
        this.submitErrors.set(extractErrors(err));
        this.submitting.set(false);
      }
    });
  }

  cancel(): void {
    this.router.navigate(['/admin']);
  }

  /**
   * Asks before a revision ends games that are still in progress. The reader's saved position
   * is a section number in the version they started on, so once the sections change there's
   * nothing sensible to resume into.
   */
  private confirmEndingGames(): boolean {
    const count = this.gamesAtRisk();
    const games = count === 1 ? '1 game is' : `${count} games are`;
    return confirm(`${games} still in progress on this book. Saving these changes will end ${count === 1 ? 'it' : 'them'}.`);
  }

  private newSection(type: SectionType, nextId = 1): FormGroup {
    const group = this.fb.group({
      id: [nextId, [Validators.required, Validators.min(1)]],
      text: ['', Validators.required],
      type: [type, Validators.required],
      options: this.fb.array(type === 'END' ? [] : [this.newOption()])
    });

    group.get('type')?.valueChanges.subscribe((newType) => {
      const optionsArray = group.get('options') as FormArray;
      if (newType === 'END') {
        optionsArray.clear();
      } else if (optionsArray.length === 0) {
        optionsArray.push(this.newOption());
      }
    });

    return group;
  }

  private newOption(): FormGroup {
    return this.fb.group({
      description: ['', Validators.required],
      gotoId: [1, [Validators.required, Validators.min(1)]]
    });
  }

  /** Builds a section group from a loaded book, options and consequences included. */
  private sectionFrom(section: SectionRequest): FormGroup {
    const group = this.newSection(section.type, section.id);
    group.patchValue({ text: section.text });

    // newSection seeds a blank option for a non-ending section; the loaded ones replace it.
    const options = group.get('options') as FormArray;
    options.clear();
    (section.options ?? []).forEach((option) => {
      const optionGroup = this.newOption();
      optionGroup.patchValue({ description: option.description, gotoId: option.gotoId });
      if (option.consequence) {
        optionGroup.addControl(
          'consequence',
          this.fb.group({
            type: [option.consequence.type, Validators.required],
            value: [option.consequence.value, [Validators.required, Validators.min(1)]],
            text: [option.consequence.text ?? '']
          })
        );
      }
      options.push(optionGroup);
    });

    return group;
  }
}

function extractErrors(err: unknown): string[] {
  const body = (err as { error?: ApiErrorResponse })?.error;
  return body?.messages?.length ? body.messages : ['Something went wrong. Please try again.'];
}
