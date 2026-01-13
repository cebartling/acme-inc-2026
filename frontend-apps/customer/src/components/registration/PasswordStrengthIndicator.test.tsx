import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { PasswordStrengthIndicator } from "./PasswordStrengthIndicator";
import type { PasswordStrength } from "@/schemas/registration.schema";

describe("PasswordStrengthIndicator", () => {
  const createStrength = (
    overrides: Partial<PasswordStrength> = {}
  ): PasswordStrength => ({
    score: 0,
    label: "Weak",
    color: "bg-red-500",
    requirements: {
      minLength: false,
      hasUppercase: false,
      hasLowercase: false,
      hasDigit: false,
      hasSpecialChar: false,
    },
    ...overrides,
  });

  describe("visibility", () => {
    it("renders nothing when show is false", () => {
      const { container } = render(
        <PasswordStrengthIndicator strength={createStrength()} show={false} />
      );
      expect(container.firstChild).toBeNull();
    });

    it("renders content when show is true", () => {
      render(
        <PasswordStrengthIndicator strength={createStrength()} show={true} />
      );
      expect(screen.getByText("Weak")).toBeInTheDocument();
    });
  });

  describe("strength label display", () => {
    it("displays Weak label with correct styling", () => {
      render(
        <PasswordStrengthIndicator
          strength={createStrength({ label: "Weak" })}
          show={true}
        />
      );
      const label = screen.getByText("Weak");
      expect(label).toBeInTheDocument();
      expect(label).toHaveClass("text-red-600");
    });

    it("displays Fair label with correct styling", () => {
      render(
        <PasswordStrengthIndicator
          strength={createStrength({
            label: "Fair",
            score: 1,
            color: "bg-orange-500",
          })}
          show={true}
        />
      );
      const label = screen.getByText("Fair");
      expect(label).toBeInTheDocument();
      expect(label).toHaveClass("text-orange-600");
    });

    it("displays Good label with correct styling", () => {
      render(
        <PasswordStrengthIndicator
          strength={createStrength({
            label: "Good",
            score: 2,
            color: "bg-yellow-500",
          })}
          show={true}
        />
      );
      const label = screen.getByText("Good");
      expect(label).toBeInTheDocument();
      expect(label).toHaveClass("text-yellow-600");
    });

    it("displays Strong label with correct styling", () => {
      render(
        <PasswordStrengthIndicator
          strength={createStrength({
            label: "Strong",
            score: 3,
            color: "bg-green-500",
          })}
          show={true}
        />
      );
      const label = screen.getByText("Strong");
      expect(label).toBeInTheDocument();
      expect(label).toHaveClass("text-green-600");
    });
  });

  describe("requirements display", () => {
    it("displays all requirement labels", () => {
      render(
        <PasswordStrengthIndicator strength={createStrength()} show={true} />
      );
      expect(screen.getByText("Minimum 8 characters")).toBeInTheDocument();
      expect(
        screen.getByText("At least one uppercase letter")
      ).toBeInTheDocument();
      expect(
        screen.getByText("At least one lowercase letter")
      ).toBeInTheDocument();
      expect(screen.getByText("At least one digit")).toBeInTheDocument();
      expect(
        screen.getByText(
          "At least one special character (!@#$%^&*()_+-=[]{}|;:,.<>?)"
        )
      ).toBeInTheDocument();
    });

    it("shows check icon for met requirements", () => {
      render(
        <PasswordStrengthIndicator
          strength={createStrength({
            requirements: {
              minLength: true,
              hasUppercase: true,
              hasLowercase: false,
              hasDigit: false,
              hasSpecialChar: false,
            },
          })}
          show={true}
        />
      );
      // Requirements that are met should have green text
      const minLengthText = screen.getByText("Minimum 8 characters");
      expect(minLengthText).toHaveClass("text-green-700");

      const uppercaseText = screen.getByText("At least one uppercase letter");
      expect(uppercaseText).toHaveClass("text-green-700");

      // Requirements not met should have gray text
      const lowercaseText = screen.getByText("At least one lowercase letter");
      expect(lowercaseText).toHaveClass("text-gray-600");
    });

    it("shows X icon for unmet requirements", () => {
      render(
        <PasswordStrengthIndicator
          strength={createStrength({
            requirements: {
              minLength: false,
              hasUppercase: false,
              hasLowercase: false,
              hasDigit: false,
              hasSpecialChar: false,
            },
          })}
          show={true}
        />
      );
      // All requirements should have gray text (not met)
      expect(screen.getByText("Minimum 8 characters")).toHaveClass(
        "text-gray-600"
      );
      expect(screen.getByText("At least one uppercase letter")).toHaveClass(
        "text-gray-600"
      );
      expect(screen.getByText("At least one lowercase letter")).toHaveClass(
        "text-gray-600"
      );
      expect(screen.getByText("At least one digit")).toHaveClass(
        "text-gray-600"
      );
    });
  });

  describe("progress bar", () => {
    it("shows 25% width for score 0", () => {
      const { container } = render(
        <PasswordStrengthIndicator
          strength={createStrength({ score: 0 })}
          show={true}
        />
      );
      const progressBar = container.querySelector('[class*="h-full"]');
      expect(progressBar).toHaveStyle({ width: "25%" });
    });

    it("shows 50% width for score 1", () => {
      const { container } = render(
        <PasswordStrengthIndicator
          strength={createStrength({ score: 1 })}
          show={true}
        />
      );
      const progressBar = container.querySelector('[class*="h-full"]');
      expect(progressBar).toHaveStyle({ width: "50%" });
    });

    it("shows 75% width for score 2", () => {
      const { container } = render(
        <PasswordStrengthIndicator
          strength={createStrength({ score: 2 })}
          show={true}
        />
      );
      const progressBar = container.querySelector('[class*="h-full"]');
      expect(progressBar).toHaveStyle({ width: "75%" });
    });

    it("shows 100% width for score 3", () => {
      const { container } = render(
        <PasswordStrengthIndicator
          strength={createStrength({ score: 3 })}
          show={true}
        />
      );
      const progressBar = container.querySelector('[class*="h-full"]');
      expect(progressBar).toHaveStyle({ width: "100%" });
    });

    it("applies correct color class to progress bar", () => {
      const { container } = render(
        <PasswordStrengthIndicator
          strength={createStrength({ color: "bg-green-500" })}
          show={true}
        />
      );
      const progressBar = container.querySelector('[class*="h-full"]');
      expect(progressBar).toHaveClass("bg-green-500");
    });
  });
});
