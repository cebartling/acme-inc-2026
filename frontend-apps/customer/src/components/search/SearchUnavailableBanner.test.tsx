import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { SearchUnavailableBanner } from "./SearchUnavailableBanner";

describe("SearchUnavailableBanner", () => {
  it("renders the exact copy required by AC-0004-09-03", () => {
    render(<SearchUnavailableBanner />);

    expect(
      screen.getByText(
        "Search is temporarily unavailable. Browse by category instead.",
      ),
    ).toBeInTheDocument();
  });

  it("points the customer at category navigation", () => {
    render(<SearchUnavailableBanner />);

    expect(screen.getByText(/Pick a category below/)).toBeInTheDocument();
  });

  it("tells the customer search returns on its own", () => {
    render(<SearchUnavailableBanner />);

    expect(
      screen.getByText(/Search will come back automatically/),
    ).toBeInTheDocument();
  });

  it("is announced to assistive technology", () => {
    render(<SearchUnavailableBanner />);

    const banner = screen.getByTestId("searchUnavailableBanner");
    expect(banner).toHaveAttribute("role", "alert");
    // polite, not assertive: an outage notice should not interrupt what the
    // screen reader is currently saying.
    expect(banner).toHaveAttribute("aria-live", "polite");
  });
});
