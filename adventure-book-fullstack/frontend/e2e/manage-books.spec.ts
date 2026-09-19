import { expect, Page, test } from '@playwright/test';

/**
 * Revising and removing books. Neither is in the brief, but without them a book published
 * with a mistake is stuck in the library forever, which makes the add-a-book feature much
 * less useful than it looks.
 *
 * <p>Each test publishes the book it works on, so nothing here depends on the seeded four —
 * and anything these tests create, they also delete.
 */

function uniqueTitle(prefix: string) {
  return `${prefix} ${Date.now()}`;
}

async function openManageLibrary(page: Page) {
  await page.goto('/');
  await page.getByRole('button', { name: /Add Your Own Adventure/ }).click();
  await expect(page.getByRole('heading', { name: 'Manage Library' })).toBeVisible();
}

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

/** Publishes a minimal two-section book and leaves the browser on the management screen. */
async function publishBook(page: Page, title: string) {
  await openManageLibrary(page);
  await page.getByRole('button', { name: /Add Adventure/ }).click();

  await page.getByLabel('Title').fill(title);
  await page.getByLabel('Author').fill('E2E Tester');
  await fillSection(page, 0, {
    id: 1,
    type: 'BEGIN',
    text: 'The original opening.',
    optionText: 'Press on',
    gotoId: 2
  });
  await page.getByRole('button', { name: /Add section/ }).click();
  await fillSection(page, 1, { id: 2, type: 'END', text: 'The original ending.' });
  await page.getByRole('button', { name: /Publish Adventure/ }).click();

  await expect(page.getByRole('heading', { name: 'Manage Library' })).toBeVisible();
}

function rowFor(page: Page, title: string) {
  return page.locator('tbody tr').filter({ hasText: title });
}

test.describe('Managing the library', () => {
  test('lists every book with the controls to revise or remove it', async ({ page }) => {
    await openManageLibrary(page);

    const crystalCaverns = rowFor(page, 'The Crystal Caverns');
    await expect(crystalCaverns).toBeVisible();
    await expect(crystalCaverns).toContainText('Evelyn Stormrider');
    await expect(crystalCaverns.getByRole('button', { name: 'Edit' })).toBeVisible();
    await expect(crystalCaverns.getByRole('button', { name: 'Delete' })).toBeVisible();
  });

  test('warns that changes here affect games in progress', async ({ page }) => {
    await openManageLibrary(page);

    await expect(page.getByText(/ends any game still in progress/)).toBeVisible();
  });

  test('opens an existing book with all of its content filled in', async ({ page }) => {
    const title = uniqueTitle('The Book To Read Back');
    await publishBook(page, title);

    await rowFor(page, title).getByRole('button', { name: 'Edit' }).click();

    await expect(page.getByRole('heading', { name: 'Revise Adventure' })).toBeVisible();
    await expect(page.getByLabel('Title')).toHaveValue(title);
    await expect(page.getByLabel('Author')).toHaveValue('E2E Tester');

    // Section text used to be dropped on load — this is the regression guard.
    const firstSection = page.locator('.section-card').nth(0);
    await expect(firstSection.getByLabel('Text', { exact: true })).toHaveValue('The original opening.');
    await expect(firstSection.getByLabel('Description', { exact: true })).toHaveValue('Press on');

    const secondSection = page.locator('.section-card').nth(1);
    await expect(secondSection.getByLabel('Text', { exact: true })).toHaveValue('The original ending.');

    // Tidy up.
    await page.getByRole('button', { name: /Back to Manage Library/ }).click();
    page.once('dialog', (dialog) => dialog.accept());
    await rowFor(page, title).getByRole('button', { name: 'Delete' }).click();
  });

  test('saves a revision and shows it in the reader library', async ({ page }) => {
    const title = uniqueTitle('The Draft');
    const revisedTitle = `${title} Revised`;
    await publishBook(page, title);

    await rowFor(page, title).getByRole('button', { name: 'Edit' }).click();
    await page.getByLabel('Title').fill(revisedTitle);
    await page.locator('.section-card').nth(0).getByLabel('Text', { exact: true })
      .fill('A completely rewritten opening.');
    await page.getByRole('button', { name: 'Save Changes' }).click();

    await expect(rowFor(page, revisedTitle)).toBeVisible();

    // The reader sees the revision, and it plays.
    await page.getByRole('button', { name: 'Back to library' }).click();
    await page.getByRole('searchbox', { name: 'Search adventures' }).fill(revisedTitle);
    await page.locator('app-book-card').filter({ hasText: revisedTitle })
      .getByRole('button', { name: /Begin Quest/ }).click();
    await expect(page.getByText('A completely rewritten opening.')).toBeVisible();

    await page.getByRole('button', { name: /Back to Library/ }).click();
    await page.getByRole('button', { name: /Add Your Own Adventure/ }).click();
    page.once('dialog', (dialog) => dialog.accept());
    await rowFor(page, revisedTitle).getByRole('button', { name: 'Delete' }).click();
  });

  test('refuses a revision that breaks a rule, and keeps the book as it was', async ({ page }) => {
    const title = uniqueTitle('The Book To Break');
    await publishBook(page, title);

    await rowFor(page, title).getByRole('button', { name: 'Edit' }).click();
    // Turn the ending into a node with nowhere to go — the book then has no ending at all.
    await page.locator('.section-card').nth(1).getByLabel('Type', { exact: true }).selectOption('NODE');
    await page.locator('.section-card').nth(1).getByLabel('Description', { exact: true }).fill('Nowhere');
    await page.locator('.section-card').nth(1).getByLabel('Goes to id', { exact: true }).fill('1');
    await page.getByRole('button', { name: 'Save Changes' }).click();

    await expect(page.getByText(/no ending section/i)).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Revise Adventure' })).toBeVisible();

    await page.getByRole('button', { name: /Back to Manage Library/ }).click();
    page.once('dialog', (dialog) => dialog.accept());
    await rowFor(page, title).getByRole('button', { name: 'Delete' }).click();
  });

  test('asks before deleting and keeps the book when refused', async ({ page }) => {
    const title = uniqueTitle('The Book To Keep');
    await publishBook(page, title);

    page.once('dialog', (dialog) => {
      expect(dialog.message()).toContain(title);
      return dialog.dismiss();
    });
    await rowFor(page, title).getByRole('button', { name: 'Delete' }).click();

    await expect(rowFor(page, title)).toBeVisible();

    page.once('dialog', (dialog) => dialog.accept());
    await rowFor(page, title).getByRole('button', { name: 'Delete' }).click();
  });

  test('removes a book from the management screen and the reader library', async ({ page }) => {
    const title = uniqueTitle('The Book To Remove');
    await publishBook(page, title);

    page.once('dialog', (dialog) => dialog.accept());
    await rowFor(page, title).getByRole('button', { name: 'Delete' }).click();

    await expect(rowFor(page, title)).toHaveCount(0);

    await page.getByRole('button', { name: 'Back to library' }).click();
    await page.getByRole('searchbox', { name: 'Search adventures' }).fill(title);
    await expect(page.getByText(/No adventures match/)).toBeVisible();
  });

  test('deleting a book also drops the game someone had in progress on it', async ({ page }) => {
    const title = uniqueTitle('The Abandoned Book');
    await publishBook(page, title);

    // Start a game, then leave it in progress.
    await page.getByRole('button', { name: 'Back to library' }).click();
    await page.getByRole('searchbox', { name: 'Search adventures' }).fill(title);
    await page.locator('app-book-card').filter({ hasText: title })
      .getByRole('button', { name: /Begin Quest/ }).click();
    await expect(page.getByText('The original opening.')).toBeVisible();
    await page.getByRole('button', { name: /Back to Library/ }).click();

    await expect(page.getByRole('button', { name: new RegExp(title) })).toBeVisible();

    await page.getByRole('button', { name: /Add Your Own Adventure/ }).click();
    page.once('dialog', (dialog) => dialog.accept());
    await rowFor(page, title).getByRole('button', { name: 'Delete' }).click();
    await expect(rowFor(page, title)).toHaveCount(0);

    // The saved game goes with it, rather than lingering as a broken entry.
    await page.getByRole('button', { name: 'Back to library' }).click();
    await expect(page.getByRole('button', { name: new RegExp(title) })).toHaveCount(0);
  });
});
