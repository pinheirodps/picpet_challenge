import { expect, test } from '@playwright/test';

/**
 * Objective 1: the library lists every book and lets the reader search and filter it.
 * Runs against the real API, so these also prove the seed data loaded.
 */
test.describe('The adventure library', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { name: /Adventure Awaits/ })).toBeVisible();
  });

  test('lists the seeded books with their author and chapter count', async ({ page }) => {
    const cards = page.locator('app-book-card');
    await expect(cards.first()).toBeVisible();
    expect(await cards.count()).toBeGreaterThanOrEqual(4);

    const crystalCaverns = cards.filter({ hasText: 'The Crystal Caverns' });
    await expect(crystalCaverns).toBeVisible();
    await expect(crystalCaverns).toContainText('Evelyn Stormrider');
    await expect(crystalCaverns).toContainText('EASY');
    await expect(crystalCaverns).toContainText(/\d+ chapters/);
  });

  test('announces how many adventures are available', async ({ page }) => {
    await expect(page.getByText(/\d+ Epic Adventures Available/)).toBeVisible();
  });

  test('searches by title', async ({ page }) => {
    await page.getByRole('searchbox', { name: 'Search adventures' }).fill('caverns');

    await expect(page.locator('app-book-card')).toHaveCount(1);
    await expect(page.locator('app-book-card')).toContainText('The Crystal Caverns');
  });

  test('searches by author', async ({ page }) => {
    await page.getByRole('searchbox', { name: 'Search adventures' }).fill('Ashfell');

    await expect(page.locator('app-book-card')).toHaveCount(1);
    await expect(page.locator('app-book-card')).toContainText('Dragon Quest');
  });

  // The search pipeline used to swallow every keystroke after the first, because
  // distinctUntilChanged sat on a Subject<void>. This is the regression guard.
  test('keeps searching as the reader changes their mind', async ({ page }) => {
    const initialCount = await page.locator('app-book-card').count();
    const search = page.getByRole('searchbox', { name: 'Search adventures' });

    await search.fill('caverns');
    await expect(page.locator('app-book-card')).toHaveCount(1);

    await search.fill('');
    await expect(page.locator('app-book-card')).toHaveCount(initialCount);

    await search.fill('dragon');
    await expect(page.locator('app-book-card')).toHaveCount(1);
    await expect(page.locator('app-book-card')).toContainText('Dragon Quest');
  });

  test('tells the reader when nothing matches', async ({ page }) => {
    await page.getByRole('searchbox', { name: 'Search adventures' }).fill('zzzz-no-such-book');

    await expect(page.getByText(/No adventures match/)).toBeVisible();
    await expect(page.locator('app-book-card')).toHaveCount(0);
  });

  test('filters by difficulty and clears the filter when pressed again', async ({ page }) => {
    const initialCount = await page.locator('app-book-card').count();
    const hard = page.getByRole('button', { name: 'HARD', exact: true });

    await hard.click();
    await expect(hard).toHaveAttribute('aria-pressed', 'true');
    const filtered = page.locator('app-book-card');
    // The two seeded HARD books; the create-book suite only ever publishes EASY ones, so
    // this stays stable even on a database that earlier runs have added to.
    await expect(filtered).toHaveCount(2); // Dragon Quest + The Prisoner
    await expect(filtered.first()).toContainText('HARD');

    await hard.click();
    await expect(hard).toHaveAttribute('aria-pressed', 'false');
    await expect(page.locator('app-book-card')).toHaveCount(initialCount);
  });

  test('combines search and filter', async ({ page }) => {
    await page.getByRole('button', { name: 'HARD', exact: true }).click();
    await page.getByRole('searchbox', { name: 'Search adventures' }).fill('prisoner');

    await expect(page.locator('app-book-card')).toHaveCount(1);
    await expect(page.locator('app-book-card')).toContainText('The Prisoner');
  });

  test('paginates only when there is more than one page of results', async ({ page }) => {
    const pagination = page.getByRole('navigation', { name: 'Library pages' });

    // A narrow search always fits on one page, whatever else the database holds — earlier
    // runs of the create-book suite may well have pushed the full library past one page.
    await page.getByRole('searchbox', { name: 'Search adventures' }).fill('caverns');
    await expect(page.locator('app-book-card')).toHaveCount(1);
    await expect(pagination).toHaveCount(0);
  });
});
