import { Duration, Interaction, Task, the, Wait } from '@serenity-js/core';
import { BrowseTheWeb } from '@serenity-js/web';

const logoutAll = process.env.DEMO_LOGOUT_ALL === 'true';

const OpenUserMenu = Interaction.where(the`#actor opens the user menu`, async (actor) => {
  const page = await BrowseTheWeb.as(actor).currentPage();
  const nativePage = await (page as any).nativePage();
  console.log('  opening user menu...');
  await nativePage.getByRole('button', { name: /open user menu/i }).click();
});

const ClickSignOut = Interaction.where(the`#actor signs out`, async (actor) => {
  const page = await BrowseTheWeb.as(actor).currentPage();
  const nativePage = await (page as any).nativePage();

  if (logoutAll) {
    console.log('  clicking Sign Out All Devices (DEMO_LOGOUT_ALL=true)...');
    await nativePage.getByRole('menuitem', { name: /sign out all devices/i }).click();
    await nativePage.getByRole('alertdialog').waitFor({ timeout: 5000 });
    await nativePage.waitForTimeout(1200);
    await nativePage.getByRole('button', { name: /^sign out all$/i }).click();
  } else {
    console.log('  clicking Sign Out...');
    await nativePage.getByRole('menuitem', { name: /^sign out$/i }).click();
  }
});

const WaitForSignoutRedirect = Interaction.where(
  the`#actor waits for the sign-out redirect`,
  async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();
    await nativePage.waitForURL(/\/signin\?.*logout=true/, { timeout: 10000 });
    console.log('  signed out, landed on /signin?logout=true');
  }
);

export const SignOut = {
  fromCurrentSession: () =>
    Task.where(
      the`#actor signs out of the current session`,
      Wait.for(Duration.ofMilliseconds(1500)),
      OpenUserMenu,
      Wait.for(Duration.ofMilliseconds(800)),
      ClickSignOut,
      WaitForSignoutRedirect,
      Wait.for(Duration.ofMilliseconds(1500))
    ),
};
