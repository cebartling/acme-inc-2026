import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { InactiveAccountMessage } from "./InactiveAccountMessage";
import { identityApi } from "@/services/api";

vi.mock("@/services/api", async () => {
  const actual =
    await vi.importActual<typeof import("@/services/api")>("@/services/api");
  return {
    ...actual,
    identityApi: {
      ...actual.identityApi,
      resendVerification: vi.fn(),
    },
  };
});

vi.mock("@/services/analytics", () => ({
  trackInactiveAccountDisplayed: vi.fn(),
}));

describe("InactiveAccountMessage", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe("accessibility", () => {
    it("uses role=alert and aria-live=assertive", () => {
      render(
        <InactiveAccountMessage
          reason="SUSPENDED"
          email="user@example.com"
          supportUrl="https://www.acme.com/support"
          supportEmail="support@acme.com"
        />,
      );
      const card = screen.getByTestId("inactive-account-message");
      expect(card).toHaveAttribute("role", "alert");
      expect(card).toHaveAttribute("aria-live", "assertive");
    });
  });

  describe("PENDING_VERIFICATION variant", () => {
    it("renders resend button and triggers the resend API", async () => {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      vi.mocked(identityApi.resendVerification).mockResolvedValueOnce({
        message: "sent",
      });

      render(
        <InactiveAccountMessage
          reason="PENDING_VERIFICATION"
          email="customer@example.com"
        />,
      );

      const button = screen.getByTestId("resend-verification-button");
      expect(button).toHaveTextContent(/Resend verification email/i);

      await user.click(button);

      expect(identityApi.resendVerification).toHaveBeenCalledWith(
        "customer@example.com",
      );
      expect(
        await screen.findByTestId("resend-verification-success"),
      ).toBeInTheDocument();
      // Cooldown begins after a successful resend.
      expect(screen.getByTestId("resend-verification-button")).toBeDisabled();
    });

    it("counts the cooldown down by one second per tick", () => {
      render(
        <InactiveAccountMessage
          reason="PENDING_VERIFICATION"
          email="customer@example.com"
          resendAvailableIn={3}
        />,
      );

      const button = screen.getByTestId("resend-verification-button");
      expect(button).toBeDisabled();
      expect(button).toHaveTextContent("Resend in 3s");

      act(() => {
        vi.advanceTimersByTime(1000);
      });
      expect(button).toHaveTextContent("Resend in 2s");

      act(() => {
        vi.advanceTimersByTime(2000);
      });
      expect(button).not.toBeDisabled();
    });
  });

  describe("SUSPENDED variant", () => {
    it("renders support URL and email and no resend button", () => {
      render(
        <InactiveAccountMessage
          reason="SUSPENDED"
          email="user@example.com"
          supportUrl="https://www.acme.com/support"
          supportEmail="support@acme.com"
        />,
      );

      expect(screen.getByTestId("contact-support-link")).toHaveAttribute(
        "href",
        "https://www.acme.com/support",
      );
      expect(screen.getByTestId("support-email-link")).toHaveAttribute(
        "href",
        "mailto:support@acme.com",
      );
      expect(
        screen.queryByTestId("resend-verification-button"),
      ).not.toBeInTheDocument();
    });
  });

  describe("DEACTIVATED variant", () => {
    it("renders reactivation link with email pre-filled and deactivation date", () => {
      render(
        <InactiveAccountMessage
          reason="DEACTIVATED"
          email="user@example.com"
          deactivatedAt="2025-12-01T00:00:00Z"
        />,
      );

      const link = screen.getByTestId("reactivate-account-link");
      expect(link).toHaveAttribute(
        "href",
        "/reactivate?email=user%40example.com",
      );
      // The formatted date is locale-dependent; assert the year is present.
      expect(screen.getByText(/2025/)).toBeInTheDocument();
    });
  });
});
