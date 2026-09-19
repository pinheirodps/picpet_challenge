import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { BookService } from '../../../core/services/book.service';
import { BookSummary } from '../../../core/models/book.model';

const PAGE_SIZE = 100;

/**
 * Managing the library: adding a book, revising one, and removing one.
 *
 * <p>Kept off the library page on purpose. The library is the reader's shelf — the suggested
 * design shows it as somewhere to pick an adventure, and putting a delete control one click
 * away from "Begin Quest" serves neither job well. Here there is room to show what an edit or
 * a delete would actually cost, which is the part that matters: both end any game still in
 * progress on that book.
 *
 * <p>This screen has no authentication in front of it. The brief doesn't ask for accounts and
 * inventing a user model wasn't the point of the exercise, but in anything real this is the
 * first thing that would sit behind a login.
 */
@Component({
  selector: 'app-admin-page',
  standalone: true,
  templateUrl: './admin-page.component.html',
  styleUrl: './admin-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminPageComponent implements OnInit {
  private readonly bookService = inject(BookService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly books = signal<BookSummary[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  /** The book currently being deleted, so its row can show progress and stay disabled. */
  readonly deletingId = signal<number | null>(null);

  ngOnInit(): void {
    this.load();
  }

  addBook(): void {
    this.router.navigate(['/admin/books/new']);
  }

  editBook(bookId: number): void {
    this.router.navigate(['/admin/books', bookId, 'edit']);
  }

  /**
   * Removes a book after confirming, then reloads the list.
   *
   * <p>The confirmation names the book, because a misclick here is unrecoverable — there's no
   * undo, and the games played on it go too.
   */
  deleteBook(book: BookSummary): void {
    if (!confirm(`Delete "${book.title}"? This also removes any games played on it, and can't be undone.`)) {
      return;
    }

    this.deletingId.set(book.id);
    this.errorMessage.set(null);

    this.bookService
      .delete(book.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.deletingId.set(null);
          this.load();
        },
        error: () => {
          this.errorMessage.set(`Could not delete "${book.title}". Please try again.`);
          this.deletingId.set(null);
        }
      });
  }

  backToLibrary(): void {
    this.router.navigate(['/']);
  }

  private load(): void {
    this.loading.set(true);
    // One large page rather than pagination: this screen is for whoever maintains the
    // library, and seeing every book at once is more useful than paging through them.
    this.bookService
      .search('', '', 0, PAGE_SIZE)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.books.set(page.content);
          this.loading.set(false);
        },
        error: () => {
          this.errorMessage.set('Could not load the library. Is the server running?');
          this.loading.set(false);
        }
      });
  }
}
