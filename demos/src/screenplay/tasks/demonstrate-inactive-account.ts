import { Duration, Interaction, Task, the, Wait } from '@serenity-js/core';
import { BrowseTheWeb } from '@serenity-js/web';
import { config } from '../../config.ts';
import { setUserStatus, type UserStatus } from '../../identity-api.ts';

const AttemptSignin = (email: string, password: string) =>
  Interaction.where(the`#actor attempts to sign in`, async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();

    await nativePage.goto(`${config.customerAppUrl}/signin`);
    await nativePage.getByRole('heading', { name: /welcome back/i }).waitFor();
    await nativePage.getByRole('textbox', { name: 'Email' }).fill(email);
    await nativePage.getByRole('textbox', { name: 'Email' }).blur();
    await nativePage.waitForTimeout(300);
    await nativePage.locator('input[id="password"]').fill(password);
    await nativePage.locator('input[id="password"]').blur();
    await nativePage.waitForTimeout(400);
    await nativePage.getByRole('button', { name: /sign in/i }).click();
  });

const SetStatus = (userId: string, status: UserStatus) =>
  Interaction.where(the`#actor sets account status to ${status}`, async () => {
    await setUserStatus(userId, status);
  });

const WaitForInactiveCard = Interaction.where(
  the`#actor sees the inactive account card`,
  async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();
    await nativePage.getByTestId('inactive-account-message').waitFor({ timeout: 5000 });
  }
);

const ClickResendVerification = Interaction.where(
  the`#actor clicks resend verification email`,
  async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();
    console.log('  clicking "Resend verification email" — expect 60s cooldown');
    await nativePage.getByTestId('resend-verification-button').click();
    await nativePage.getByTestId('resend-verification-success').waitFor({ timeout: 5000 });
  }
);

const HoverSupportLinks = Interaction.where(
  the`#actor hovers the support links`,
  async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();
    await nativePage.getByTestId('contact-support-link').hover();
    await nativePage.waitForTimeout(2500);
    await nativePage.getByTestId('support-email-link').hover();
  }
);

const ClickReactivateAndSubmit = (email: string, password: string) =>
  Interaction.where(the`#actor reactivates the account`, async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();

    console.log('  clicking "Reactivate Account" — navigates to /reactivate');
    await nativePage.getByTestId('reactivate-account-link').click();
    await nativePage.waitForURL((url: URL) => url.pathname.endsWith('/reactivate'), {
      timeout: 5000,
    });
    await nativePage.getByTestId('reactivate-form').waitFor({ timeout: 5000 });

    const reactivateEmail = nativePage.getByTestId('reactivate-email-input');
    const currentValue = await reactivateEmail.inputValue();
    if (!currentValue) {
      await reactivateEmail.fill(email);
    }
    await nativePage.waitForTimeout(500);
    await nativePage.getByTestId('reactivate-password-input').fill(password);
    await nativePage.waitForTimeout(700);

    await nativePage.getByTestId('reactivate-submit').click();
    await nativePage.getByTestId('reactivate-sent').waitFor({ timeout: 8000 });
  });

export const DemonstrateInactiveAccount = {
  forUser: (userId: string, email: string, password: string) =>
    Task.where(
      the`#actor demonstrates inactive account variants`,

      // Variant 1: PENDING_VERIFICATION
      Interaction.where(the`#actor shows pending verification variant`, async () => {
        console.log('  variant 1: PENDING_VERIFICATION — "check your inbox" card');
      }),
      SetStatus(userId, 'PENDING_VERIFICATION'),
      AttemptSignin(email, password),
      WaitForInactiveCard,
      Wait.for(Duration.ofMilliseconds(2500)),
      ClickResendVerification,
      Wait.for(Duration.ofMilliseconds(3500)),

      // Variant 2: SUSPENDED
      Interaction.where(the`#actor shows suspended variant`, async () => {
        console.log('  variant 2: SUSPENDED — contact-support card');
      }),
      SetStatus(userId, 'SUSPENDED'),
      AttemptSignin(email, password),
      WaitForInactiveCard,
      HoverSupportLinks,
      Wait.for(Duration.ofMilliseconds(2000)),

      // Variant 3: DEACTIVATED
      Interaction.where(the`#actor shows deactivated variant`, async () => {
        console.log('  variant 3: DEACTIVATED — reactivation CTA');
      }),
      SetStatus(userId, 'DEACTIVATED'),
      AttemptSignin(email, password),
      WaitForInactiveCard,
      Wait.for(Duration.ofMilliseconds(2000)),
      ClickReactivateAndSubmit(email, password),
      Wait.for(Duration.ofMilliseconds(3000))
    ),
};
