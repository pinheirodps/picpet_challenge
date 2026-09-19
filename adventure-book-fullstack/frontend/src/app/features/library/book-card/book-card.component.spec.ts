import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { BookCardComponent } from './book-card.component';

describe('BookCardComponent', () => {
  let fixture: ComponentFixture<BookCardComponent>;
  let component: BookCardComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [BookCardComponent] });
    fixture = TestBed.createComponent(BookCardComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('book', {
      id: 1,
      title: 'The Crystal Caverns',
      author: 'Evelyn Stormrider',
      difficulty: 'EASY',
      sectionCount: 12
    });
    fixture.detectChanges();
  });

  it('renders the book title and author', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('The Crystal Caverns');
    expect(text).toContain('Evelyn Stormrider');
  });

  it('shows the chapter count', () => {
    expect(fixture.nativeElement.textContent).toContain('12 chapters');
  });

  it('uses the singular form for a one-section book', () => {
    fixture.componentRef.setInput('book', {
      id: 2,
      title: 'A Short Tale',
      author: 'Someone',
      difficulty: 'EASY',
      sectionCount: 1
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('1 chapter');
    expect(fixture.nativeElement.textContent).not.toContain('1 chapters');
  });

  it('emits beginQuest with the book id when the button is clicked', () => {
    spyOn(component.beginQuest, 'emit');

    fixture.debugElement.query(By.css('.card__cta')).nativeElement.click();

    expect(component.beginQuest.emit).toHaveBeenCalledWith(1);
  });
});
