import { expect, Page, test } from '@playwright/test';

/**
 * Objective 5: readers can add their own book. The form deliberately does not re-implement
 * the book rules — it submits and shows whatever the backend rejected — so these tests
 * check both that a good book is accepted and that the server's reasons reach the screen.
 */

/** Titles are unique per run so repeated runs don't collide in the shared database. */
function uniqueTitle(prefix: string) {
  return `${prefix} ${Date.now()}`;
}

async function openEditor(page: Page) {
  await page.goto('/');
  // Adding a book goes through the management screen, which is where every change to the
  // catalogue lives.
  await page.getByRole('button', { name: /Add Your Own Adventure/ }).click();
  await expect(page.getByRole('heading', { name: 'Manage Library' })).toBeVisible();
  await page.getByRole('button', { name: /Add Adventure/ }).click();
  await expect(page.getByRole('heading', { name: 'Add a New Adventure' })).toBeVisible();
}

/** Fills the section at `index`, which the form always creates with one option. */
async function fillSection(
  page: Page,
  index: number,
  { id, type, text, optionText, gotoId }:
    { id: number; type: 'BEGIN' | 'NODE' | 'END'; text: string; optionText?: string; gotoId?: number }
) {
  const section = page.locator('.section-card').nth(index);
  await section.getByLabel('Id', { exact: true }).fill(String(id));
  await section.getByLabel('Type', { exact: true }).selectOption(type);
  await section.getByLabel('Text', { exact: true }).fill(text);

  if (optionText !== undefined && gotoId !== undefined) {
    await section.getByLabel('Description', { exact: true }).fill(optionText);
    await section.getByLabel('Goes to id', { exact: true }).fill(String(gotoId));
  }
}

test.describe('Adding a book', () => {
  test('publishes a valid book and shows it in the library', async ({ page }) => {
    const title = uniqueTitle('The Whispering Woods');
    await openEditor(page);

    await page.getByLabel('Title').fill(title);
    await page.getByLabel('Author').fill('E2E Tester');
    await fillSection(page, 0, {
      id: 1,
      type: 'BEGIN',
      text: 'You step between the trees.',
      optionText: 'Go deeper',
      gotoId: 2
    });

    await page.getByRole('button', { name: /Add section/ }).click();
    await fillSection(page, 1, { id: 2, type: 'END', text: 'You reach a quiet clearing.' });

    await page.getByRole('button', { name: /Publish Adventure/ }).click();

    // Publishing returns to the management screen, where the new book is listed.
    await expect(page.getByRole('heading', { name: 'Manage Library' })).toBeVisible();
    await expect(page.locator('tbody tr').filter({ hasText: title })).toBeVisible();

    // And it reaches the reader's library too.
    await page.getByRole('button', { name: 'Back to library' }).click();
    await page.getByRole('searchbox', { name: 'Search adventures' }).fill(title);
    await expect(page.locator('app-book-card').filter({ hasText: title })).toBeVisible();
  });

  test('a published book can be played immediately', async ({ page }) => {
    const title = uniqueTitle('The Playable Path');
    await openEditor(page);

    await page.getByLabel('Title').fill(title);
    await fillSection(page, 0, {
      id: 1,
      type: 'BEGIN',
      text: 'A single fork in the road.',
      optionText: 'Take it',
      gotoId: 2
    });
    await page.getByRole('button', { name: /Add section/ }).click();
    await fillSection(page, 1, { id: 2, type: 'END', text: 'And that was that.' });
    await page.getByRole('button', { name: /Publish Adventure/ }).click();

    await page.getByRole('button', { name: 'Back to library' }).click();
    await page.getByRole('searchbox', { name: 'Search adventures' }).fill(title);
    await page
      .locator('app-book-card')
      .filter({ hasText: title })
      .getByRole('button', { name: /Begin Quest/ })
      .click();

    await expect(page.getByText('A single fork in the road.')).toBeVisible();
    await page.getByRole('button', { name: /Take it/ }).click();
    await expect(page.getByRole('heading', { name: 'The End' })).toBeVisible();
  });

  test('refuses a book with no ending, quoting the rule that failed', async ({ page }) => {
    await openEditor(page);

    await page.getByLabel('Title').fill(uniqueTitle('No Way Out'));
    await fillSection(page, 0, {
      id: 1,
      type: 'BEGIN',
      text: 'It goes on forever.',
      optionText: 'Keep going',
      gotoId: 1
    });

    await page.getByRole('button', { name: /Publish Adventure/ }).click();

    await expect(page.getByRole('alert')).toContainText('Book has no ending section');
    // Still on the editor — nothing was saved.
    await expect(page.getByRole('heading', { name: 'Add a New Adventure' })).toBeVisible();
  });

  test('refuses an option pointing at a section that does not exist', async ({ page }) => {
    await openEditor(page);

    await page.getByLabel('Title').fill(uniqueTitle('Dangling'));
    await fillSection(page, 0, {
      id: 1,
      type: 'BEGIN',
      text: 'Off into nowhere.',
      optionText: 'Step through',
      gotoId: 99
    });
    await page.getByRole('button', { name: /Add section/ }).click();
    await fillSection(page, 1, { id: 2, type: 'END', text: 'Unreachable.' });

    await page.getByRole('button', { name: /Publish Adventure/ }).click();

    await expect(page.getByRole('alert')).toContainText('non-existent section 99');
  });

  test('will not submit while a required field is empty', async ({ page }) => {
    await openEditor(page);

    // Title left blank on purpose.
    await page.getByRole('button', { name: /Publish Adventure/ }).click();

    await expect(page.getByText('A title is required.')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Add a New Adventure' })).toBeVisible();
  });

  test('sections and options can be added and removed', async ({ page }) => {
    await openEditor(page);

    await expect(page.locator('.section-card')).toHaveCount(1);
    await page.getByRole('button', { name: /Add section/ }).click();
    await expect(page.locator('.section-card')).toHaveCount(2);

    await page.locator('.section-card').nth(1).getByRole('button', { name: 'Remove section' }).click();
    await expect(page.locator('.section-card')).toHaveCount(1);

    const firstSection = page.locator('.section-card').first();
    await expect(firstSection.locator('.option-card')).toHaveCount(1);
    await firstSection.getByRole('button', { name: /Add option/ }).click();
    await expect(firstSection.locator('.option-card')).toHaveCount(2);
  });

  test('an option can carry a consequence', async ({ page }) => {
    await openEditor(page);
    const firstOption = page.locator('.option-card').first();

    await firstOption.getByRole('button', { name: /Add consequence/ }).click();

    await expect(firstOption.getByLabel('Effect')).toBeVisible();
    await expect(firstOption.getByLabel('Amount')).toBeVisible();

    await firstOption.getByRole('button', { name: /Remove consequence/ }).click();
    await expect(firstOption.getByLabel('Effect')).toHaveCount(0);
  });
});
