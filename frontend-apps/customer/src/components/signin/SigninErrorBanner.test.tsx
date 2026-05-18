import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { SigninErrorBanner } from "./SigninErrorBanner";

describe("SigninErrorBanner", () => {
  describe("accessibility", () => {
    it("uses role=alert and aria-live=assertive", () => {
      render(<SigninErrorBanner message="Invalid email or password." />);
      const banner = screen.getByTestId("signin-error-banner");
      expect(banner).toHaveAttribute("role", "alert");
      expect(banner).toHaveAttribute("aria-live", "assertive");
    });
  });

  describe("message", () => {
    it("renders the generic error message", () => {
      render(<SigninErrorBanner message="Invalid email or password." />);
      expect(
        screen.getByText("Invalid email or password.")
      ).toBeInTheDocument();
    });

    it("does not show remaining attempts when undefined", () => {
      render(<SigninErrorBanner message="Invalid email or password." />);
      expect(
        screen.queryByTestId("signin-remaining-attempts")
      ).not.toBeInTheDocument();
    });
  });

  describe("remaining attempts", () => {
    it("renders plural attempts when more than one remaining", () => {
      render(
        <SigninErrorBanner
          message="Invalid email or password."
          remainingAttempts={3}
        />
      );
      expect(
        screen.getByTestId("signin-remaining-attempts")
      ).toHaveTextContent("3 attempts remaining.");
    });

    it("renders singular attempt when one remaining", () => {
      render(
        <SigninErrorBanner
          message="Invalid email or password."
          remainingAttempts={1}
        />
      );
      expect(
        screen.getByTestId("signin-remaining-attempts")
      ).toHaveTextContent("1 attempt remaining before account lockout.");
    });
  });

  describe("urgent variant", () => {
    it("uses urgent style and warning copy when remainingAttempts <= 2", () => {
      render(
        <SigninErrorBanner
          message="Invalid email or password."
          remainingAttempts={2}
        />
      );
      expect(
        screen.getByTestId("signin-remaining-attempts")
      ).toHaveTextContent("before account lockout.");
      expect(screen.getByTestId("signin-error-reset-link")).toHaveTextContent(
        "Reset Password"
      );
    });

    it("uses standard style when remainingAttempts > 2", () => {
      render(
        <SigninErrorBanner
          message="Invalid email or password."
          remainingAttempts={3}
        />
      );
      expect(screen.getByTestId("signin-error-reset-link")).toHaveTextContent(
        "Reset it here"
      );
    });
  });

  describe("forgot password link", () => {
    it("links to /forgot-password", () => {
      render(<SigninErrorBanner message="Invalid email or password." />);
      expect(screen.getByTestId("signin-error-reset-link")).toHaveAttribute(
        "href",
        "/forgot-password"
      );
    });
  });
});
