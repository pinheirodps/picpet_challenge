import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { BookSummary } from '../../../core/models/book.model';

/** One book in the library grid, with its difficulty badge and a "Begin Quest" action. */
@Component({
  selector: 'app-book-card',
  standalone: true,
  templateUrl: './book-card.component.html',
  styleUrl: './book-card.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BookCardComponent {
  readonly book = input.required<BookSummary>();
  readonly beginQuest = output<number>();

  onBeginQuest(): void {
    this.beginQuest.emit(this.book().id);
  }
}
