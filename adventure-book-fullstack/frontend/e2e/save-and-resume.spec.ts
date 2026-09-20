import { expect, Page, test } from '@playwright/test';

/**
 * Objective 4: progress is saved without the reader asking, and they can pick a game back
 * up later. There is no save request — every choice persists the session — so what these
 * tests prove is that leaving and coming back really does restore the same state.
 */

/**
 * Opens a book at its first section.
 *
 * <p>Beginning a book that already has a game in progress resumes it rather than starting a
 * second one — right for a reader, but it means a test can't assume a fresh start, so any
 * leftover game on that book is stopped first.
 */
async function beginQuest(page: Page, bookTitle: string) {
  await page.goto('/');
  await stopAnyGameOf(page, bookTitle);
  await page
    .locator('app-book-card')
    .filter({ hasText: bookTitle })
    .getByRole('button', { name: /Begin Quest/ })
    .click();
}

/** Ends any in-progress game on a book, so the next "Begin Quest" really does begin. */
async function stopAnyGameOf(page: Page, bookTitle: string) {
  const resumeCard = page.locator('.continue__item').filter({ hasText: bookTitle });

  // Continue Playing collapses to the four most recent, so an older game could be hidden.
  const showAll = page.getByRole('button', { name: /^Show all/ });
  if (await showAll.isVisible().catch(() => false)) {
    await showAll.click();
  }

  while ((await resumeCard.count()) > 0) {
    await resumeCard.first().click();
    // Stopping asks before it ends the game; this is cleanup, not the thing under test.
    page.once('dialog', (dialog) => dialog.accept());
    await page.getByRole('button', { name: /Stop/ }).click();
    await expect(page.getByRole('heading', { name: 'Adventure Stopped' })).toBeVisible();
    await page.getByRole('button', { name: /Return to the Library/ }).click();
    await expect(page.getByRole('heading', { name: /Adventure Awaits/ })).toBeVisible();
  }
}

test.describe('Saving and resuming', () => {
  test('a game in progress appears under Continue Playing', async ({ page }) => {
    await beginQuest(page, 'Dragon Quest');
    await page.getByRole('button', { name: /Climb the exposed cliff path/ }).click();
    await expect(page.getByText(/The cliff path is steep/)).toBeVisible();

    await page.getByRole('button', { name: /Back to Library/ }).click();

    const resumeCard = page.locator('.continue__item').filter({ hasText: 'Dragon Quest' });
    await expect(page.getByRole('heading', { name: /Continue Playing/ })).toBeVisible();
    await expect(resumeCard.first()).toBeVisible();
  });

  test('resuming returns the reader to exactly where they stopped', async ({ page }) => {
    await beginQuest(page, 'Dragon Quest');
    await page.getByRole('button', { name: /Follow the old miners' tunnel/ }).click();
    await expect(page.getByText(/The tunnel is pitch black/)).toBeVisible();

    await page.getByRole('button', { name: /Back to Library/ }).click();
    await page.locator('.continue__item').filter({ hasText: 'Dragon Quest' }).first().click();

    // Back in the tunnel, not at the foot of the mountain.
    await expect(page.getByText(/The tunnel is pitch black/)).toBeVisible();
    await expect(page.getByText(/Smoke rises from the northern peaks/)).toHaveCount(0);
  });

  test('a resumed game keeps the health it had', async ({ page }) => {
    await beginQuest(page, 'The Crystal Caverns');
    await page.getByRole('button', { name: /Cross the rope bridge/ }).click();
    await page.getByRole('button', { name: /Try to jump to the other side/ }).click();
    await expect(page.getByLabel(/Health: 3 of 10/)).toBeVisible();

    await page.getByRole('button', { name: /Back to Library/ }).click();
    await page.locator('.continue__item').filter({ hasText: 'The Crystal Caverns' }).first().click();

    await expect(page.getByLabel(/Health: 3 of 10/)).toBeVisible();
  });

  // Leaving mid-game is the pause: the game stays resumable under Continue Playing. Saving
  // is a separate control that acknowledges progress without leaving.
  test('leaving mid-game keeps it resumable', async ({ page }) => {
    await beginQuest(page, 'Dragon Quest');
    await page.getByRole('button', { name: /Climb the exposed cliff path/ }).click();
    await expect(page.getByText(/The cliff path is steep/)).toBeVisible();

    await page.getByRole('button', { name: /Back to Library/ }).click();

    await expect(page.getByRole('heading', { name: /Adventure Awaits/ })).toBeVisible();
    const resumeCard = page.locator('.continue__item').filter({ hasText: 'Dragon Quest' });
    await expect(resumeCard).toHaveCount(1);

    // And it really does resume where it was left.
    await resumeCard.click();
    await expect(page.getByText(/The cliff path is steep/)).toBeVisible();

    page.once('dialog', (dialog) => dialog.accept());
    await page.getByRole('button', { name: /Stop/ }).click();
  });

  // Pressing "Begin Quest" on a book already part-way through used to start a second game,
  // leaving two entries in Continue Playing that looked identical.
  test('beginning a book already in progress resumes it instead of starting over', async ({ page }) => {
    await beginQuest(page, 'The Crystal Caverns');
    await page.getByRole('button', { name: /Search the rocky walls/ }).click();
    await expect(page.getByText(/Your hands brush against the cold stone/)).toBeVisible();

    await page.getByRole('button', { name: /Back to Library/ }).click();

    // Deliberately pressing Begin Quest again, without the cleanup beginQuest() does — that
    // second press is the whole point of this test.
    await page
      .locator('app-book-card')
      .filter({ hasText: 'The Crystal Caverns' })
      .getByRole('button', { name: /Begin Quest/ })
      .click();

    // Back where it was left, not at the opening section.
    await expect(page.getByText(/Your hands brush against the cold stone/)).toBeVisible();

    await page.getByRole('button', { name: /Back to Library/ }).click();
    const caverns = page.locator('.continue__item').filter({ hasText: 'The Crystal Caverns' });
    await expect(caverns).toHaveCount(1);

    // Leave no game behind: the specs share a database, and a lingering one would show up in
    // another test's Continue Playing list.
    await caverns.click();
    page.once('dialog', (dialog) => dialog.accept());
    await page.getByRole('button', { name: /Stop/ }).click();
    await expect(page.getByRole('heading', { name: 'Adventure Stopped' })).toBeVisible();
  });
});

test.describe('Stopping a game', () => {
  // Stop is the deliberate end, not the pause — leaving via Back to Library is the pause.
  // Because a stopped game can't be resumed, it asks first.
  test('stopping asks before it ends the adventure, and keeps it when refused', async ({ page }) => {
    await beginQuest(page, 'Pirates of the Jade Sea');
    await expect(page.getByText(/The salty breeze carries the cries of distant gulls/)).toBeVisible();

    page.once('dialog', (dialog) => {
      expect(dialog.message()).toMatch(/not be able to resume/i);
      return dialog.dismiss();
    });
    await page.getByRole('button', { name: /Stop/ }).click();

    // Refused: still playable.
    await expect(page.getByRole('heading', { name: 'Adventure Stopped' })).toHaveCount(0);
    await expect(page.locator('.option').first()).toBeVisible();
  });

  test('stopping ends the adventure and removes it from Continue Playing', async ({ page }) => {
    await beginQuest(page, 'Pirates of the Jade Sea');
    await expect(page.getByText(/The salty breeze carries the cries of distant gulls/)).toBeVisible();

    page.once('dialog', (dialog) => dialog.accept());
    await page.getByRole('button', { name: /Stop/ }).click();

    await expect(page.getByRole('heading', { name: 'Adventure Stopped' })).toBeVisible();
    await expect(page.locator('.option')).toHaveCount(0);

    await page.getByRole('button', { name: /Return to the Library/ }).click();
    await expect(page.getByRole('button', { name: /Pirates of the Jade Sea/ })).toHaveCount(0);
  });
});
