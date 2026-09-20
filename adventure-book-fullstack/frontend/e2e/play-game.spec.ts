import { expect, Page, test } from '@playwright/test';

/**
 * Objectives 2 and 3: playing through a book, and the consequences of doing so badly.
 *
 * The Crystal Caverns is used throughout because its shape is known: section 1 offers a
 * safe path and a bridge, and crossing the bridge then jumping costs 7 health — enough to
 * exercise the consequence banner without ending the game.
 */

/**
 * Opens The Crystal Caverns at its first section.
 *
 * <p>Beginning a book that already has a game in progress resumes that game rather than
 * starting a second one, which is the right behaviour for a reader but means a test can't
 * assume a fresh start. So any game left over from an earlier test is stopped first.
 */
async function startCrystalCaverns(page: Page) {
  await page.goto('/');
  await stopAnyGameOf(page, 'The Crystal Caverns');
  await page
    .locator('app-book-card')
    .filter({ hasText: 'The Crystal Caverns' })
    .getByRole('button', { name: /Begin Quest/ })
    .click();
  await expect(page.getByText('The Crystal Caverns')).toBeVisible();
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

/** Picks a choice by its visible text rather than its index, so it survives reordering. */
async function choose(page: Page, text: string | RegExp) {
  await page.getByRole('button', { name: text }).click();
}

test.describe('Playing a book', () => {
  test('starts at the beginning section with full health', async ({ page }) => {
    await startCrystalCaverns(page);

    await expect(page.getByText(/You stand at the entrance of the legendary Crystal Caverns/)).toBeVisible();
    await expect(page.getByLabel(/Health: 10 of 10/)).toBeVisible();
    await expect(page.getByRole('button', { name: /Cross the rope bridge/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /Search the rocky walls/ })).toBeVisible();
  });

  test('moves to the next section when a choice is made', async ({ page }) => {
    await startCrystalCaverns(page);

    await choose(page, /Cross the rope bridge/);

    await expect(page.getByText(/The bridge creaks under your weight/)).toBeVisible();
    await expect(page.getByLabel(/Health: 10 of 10/)).toBeVisible();
  });

  test('a harmful choice costs health and explains why', async ({ page }) => {
    await startCrystalCaverns(page);
    await choose(page, /Cross the rope bridge/);

    await choose(page, /Try to jump to the other side/);

    // The flavour text the books ship with has to reach the player — it used to be dropped.
    await expect(page.getByText('You land hard and twist your ankle.')).toBeVisible();
    await expect(page.getByText('-7 HP')).toBeVisible();
    await expect(page.getByLabel(/Health: 3 of 10/)).toBeVisible();
  });

  test('a safe choice clears the previous consequence banner', async ({ page }) => {
    await startCrystalCaverns(page);
    await choose(page, /Cross the rope bridge/);
    await choose(page, /Try to jump to the other side/);
    await expect(page.getByText('-7 HP')).toBeVisible();

    await choose(page, /Continue deeper into the cavern/);

    await expect(page.getByText('-7 HP')).toHaveCount(0);
  });

  test('reaching an ending finishes the game without claiming a win', async ({ page }) => {
    await startCrystalCaverns(page);

    // 1 → 20 → 200 → 500 → 800 (an END section)
    await choose(page, /Search the rocky walls/);
    await choose(page, /Enter the crevice/);
    await choose(page, /Take the gemstone/);
    await choose(page, /Enter the hidden passage/);

    await expect(page.getByRole('heading', { name: 'The End' })).toBeVisible();
    await expect(page.getByText(/giant crystal throne room/)).toBeVisible();
    // An ending is not necessarily a victory, so the wording stays neutral.
    await expect(page.getByText(/You Have Perished/)).toHaveCount(0);
    await expect(page.getByRole('button', { name: /Choose a New Adventure/ })).toBeVisible();
  });

  test('an ended game offers no further choices', async ({ page }) => {
    await startCrystalCaverns(page);
    await choose(page, /Search the rocky walls/);
    await choose(page, /Enter the crevice/);
    await choose(page, /Take the gemstone/);
    await choose(page, /Enter the hidden passage/);

    await expect(page.locator('.option')).toHaveCount(0);
    // An ended game has nothing left to save, pause or stop.
    await expect(page.getByRole('button', { name: /Save Progress/ })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /Pause/ })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /Stop/ })).toHaveCount(0);
    // The way back to the library is still there.
    await expect(page.getByRole('button', { name: /Back to Library/ })).toBeVisible();
  });

  // The brief asks the header to let the reader "stop/pause the game", show the book name and
  // their life, and save their progression.
  test('the header carries everything the brief asks for', async ({ page }) => {
    await startCrystalCaverns(page);

    await expect(page.getByText('The Crystal Caverns')).toBeVisible();
    await expect(page.getByLabel(/Health: 10 of 10/)).toBeVisible();
    await expect(page.getByRole('button', { name: /Back to Library/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /Save Progress/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /Pause/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /Stop/ })).toBeVisible();
  });

  test('saving acknowledges without leaving the game', async ({ page }) => {
    await startCrystalCaverns(page);

    await page.getByRole('button', { name: /Save Progress/ }).click();

    await expect(page.getByRole('button', { name: /Saved/ })).toBeVisible();
    // Still playing — saving is not leaving.
    await expect(page.locator('.option').first()).toBeVisible();
  });

  test('pausing leaves the game and keeps it resumable', async ({ page }) => {
    await startCrystalCaverns(page);
    await choose(page, /Cross the rope bridge/);
    await expect(page.getByText(/The bridge creaks under your weight/)).toBeVisible();

    await page.getByRole('button', { name: /Pause/ }).click();

    await expect(page.getByRole('heading', { name: /Adventure Awaits/ })).toBeVisible();
    const resumed = page.locator('.continue__item').filter({ hasText: 'The Crystal Caverns' });
    await expect(resumed).toHaveCount(1);

    await resumed.click();
    await expect(page.getByText(/The bridge creaks under your weight/)).toBeVisible();
  });

  test('health shows as low once it is down to three', async ({ page }) => {
    await startCrystalCaverns(page);
    await choose(page, /Cross the rope bridge/);
    await choose(page, /Try to jump to the other side/);

    await expect(page.locator('.health--low')).toBeVisible();
  });

  test('leaves the game from the header, keeping it resumable', async ({ page }) => {
    await startCrystalCaverns(page);

    await page.getByRole('button', { name: /Back to Library/ }).click();

    await expect(page.getByRole('heading', { name: /Adventure Awaits/ })).toBeVisible();
    await expect(
      page.locator('.continue__item').filter({ hasText: 'The Crystal Caverns' })
    ).toHaveCount(1);
  });
});

test.describe('Dying', () => {
  test('running out of health ends the adventure and names the cause', async ({ page }) => {
    // The Prisoner is the reliable way to die: looking under the bed costs 6, then
    // gathering your thoughts costs 3 more — but first the door route costs 3 a time.
    await page.goto('/');
    await page
      .locator('app-book-card')
      .filter({ hasText: 'The Prisoner' })
      .getByRole('button', { name: /Begin Quest/ })
      .click();

    await expect(page.getByText(/You wake up in what seems to be a dark prison cell/)).toBeVisible();

    // Loop the "door is locked → gather your thoughts" path, which costs 3 health each time (10 -> 7 -> 4 -> 1 -> 0).
    for (let attempt = 0; attempt < 4; attempt++) {
      await page.getByRole('button', { name: /You try to open the door/ }).click();
      await page.getByRole('button', { name: /Gather your thoughts/ }).click();
    }

    await expect(page.getByRole('heading', { name: 'You Have Perished' })).toBeVisible();
    await expect(page.getByText(/getting a little more crazier/)).toBeVisible();
    await expect(page.getByLabel(/Health: 0 of 10/)).toBeVisible();
    await expect(page.getByRole('button', { name: /Return to the Library/ })).toBeVisible();
  });
});
