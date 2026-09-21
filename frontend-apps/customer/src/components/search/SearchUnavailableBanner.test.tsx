import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SearchUnavailableBanner } from "./SearchUnavailableBanner";

describe("SearchUnavailableBanner", () => {
  it("renders the exact copy required by AC-0004-09-03", () => {
    render(<SearchUnavailableBanner onRetry={vi.fn()} />);

    expect(
      screen.getByText(
        "Search is temporarily unavailable. Browse by category instead.",
      ),
    ).toBeInTheDocument();
  });

  it("points the customer at category navigation", () => {
    render(<SearchUnavailableBanner onRetry={vi.fn()} />);

    expect(screen.getByText(/Pick a category below/)).toBeInTheDocument();
  });

  it("tells the customer search returns on its own", () => {
    render(<SearchUnavailableBanner onRetry={vi.fn()} />);

    expect(
      screen.getByText(/Search will come back automatically/),
    ).toBeInTheDocument();
  });

  it("is announced to assistive technology", () => {
    render(<SearchUnavailableBanner onRetry={vi.fn()} />);

    const banner = screen.getByTestId("searchUnavailableBanner");
    expect(banner).toHaveAttribute("role", "alert");
    // polite, not assertive: an outage notice should not interrupt what the
    // screen reader is currently saying.
    expect(banner).toHaveAttribute("aria-live", "polite");
  });

  describe("retry control", () => {
    it("calls onRetry when clicked", async () => {
      const onRetry = vi.fn();
      const user = userEvent.setup();
      render(<SearchUnavailableBanner onRetry={onRetry} />);

      await user.click(screen.getByTestId("searchRetryButton"));

      expect(onRetry).toHaveBeenCalledTimes(1);
    });

    it("is enabled and idle by default", () => {
      render(<SearchUnavailableBanner onRetry={vi.fn()} />);

      const button = screen.getByTestId("searchRetryButton");
      expect(button).toBeEnabled();
      expect(button).toHaveTextContent("Try search again");
    });

    it("reports progress and blocks repeat clicks while retrying", async () => {
      const onRetry = vi.fn();
      const user = userEvent.setup();
      render(<SearchUnavailableBanner onRetry={onRetry} isRetrying />);

      const button = screen.getByTestId("searchRetryButton");
      expect(button).toBeDisabled();
      expect(button).toHaveTextContent("Trying again...");

      await user.click(button);
      expect(onRetry).not.toHaveBeenCalled();
    });
  });
});
