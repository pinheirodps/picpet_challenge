import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BookSummary, Page } from '../models/book.model';
import { BookDetail, CreateBookRequest } from '../models/create-book.model';

/**
 * Talks to the backend's /api/books endpoints. Kept thin on purpose — it only shapes HTTP
 * calls, the components decide what to do with the results (loading state, error display).
 */
@Injectable({ providedIn: 'root' })
export class BookService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/books`;

  search(query: string, difficulty: string, page: number, size = 12): Observable<Page<BookSummary>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (query) {
      params = params.set('query', query);
    }
    if (difficulty) {
      params = params.set('difficulty', difficulty);
    }
    return this.http.get<Page<BookSummary>>(this.baseUrl, { params });
  }

  create(request: CreateBookRequest): Observable<BookSummary> {
    return this.http.post<BookSummary>(this.baseUrl, request);
  }

  /** Loads a whole book — sections and options included — for editing. */
  get(bookId: number): Observable<BookDetail> {
    return this.http.get<BookDetail>(`${this.baseUrl}/${bookId}`);
  }

  /** Replaces a book's content. The backend ends any game still in progress on it. */
  update(bookId: number, request: CreateBookRequest): Observable<BookSummary> {
    return this.http.put<BookSummary>(`${this.baseUrl}/${bookId}`, request);
  }

  /** Removes a book and every game played on it. */
  delete(bookId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${bookId}`);
  }
}
