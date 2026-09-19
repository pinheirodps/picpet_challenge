import { Routes } from '@angular/router';
import { LibraryPageComponent } from './features/library/library-page/library-page.component';
import { GamePageComponent } from './features/game/game-page/game-page.component';
import { BookEditorPageComponent } from './features/book-editor/book-editor-page/book-editor-page.component';
import { AdminPageComponent } from './features/admin/admin-page/admin-page.component';

/**
 * The reader's routes and the maintainer's routes are deliberately separate. Everything that
 * changes the catalogue lives under `/admin`, so the library stays what the suggested design
 * shows it as: somewhere to pick an adventure.
 *
 * <p>`/create` is kept as a redirect because it was the original route for adding a book, and
 * a bookmark to it shouldn't land on a 404.
 */
export const routes: Routes = [
  { path: '', component: LibraryPageComponent },
  { path: 'admin', component: AdminPageComponent },
  { path: 'admin/books/new', component: BookEditorPageComponent },
  { path: 'admin/books/:bookId/edit', component: BookEditorPageComponent },
  { path: 'create', redirectTo: 'admin/books/new' },
  { path: 'play/resume/:gameId', component: GamePageComponent },
  { path: 'play/:bookId', component: GamePageComponent },
  { path: '**', redirectTo: '' }
];
