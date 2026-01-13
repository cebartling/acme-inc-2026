import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { FormField } from "./FormField";

describe("FormField", () => {
  describe("label rendering", () => {
    it("renders label text", () => {
      render(
        <FormField label="Email" htmlFor="email">
          <input id="email" />
        </FormField>
      );
      expect(screen.getByText("Email")).toBeInTheDocument();
    });

    it("associates label with input via htmlFor", () => {
      render(
        <FormField label="Email" htmlFor="email">
          <input id="email" />
        </FormField>
      );
      const label = screen.getByText("Email");
      expect(label).toHaveAttribute("for", "email");
    });

    it("shows required indicator when required is true", () => {
      render(
        <FormField label="Email" htmlFor="email" required>
          <input id="email" />
        </FormField>
      );
      expect(screen.getByText("*")).toBeInTheDocument();
    });

    it("does not show required indicator when required is false", () => {
      render(
        <FormField label="Email" htmlFor="email" required={false}>
          <input id="email" />
        </FormField>
      );
      expect(screen.queryByText("*")).not.toBeInTheDocument();
    });
  });

  describe("error display", () => {
    it("does not show error when not touched", () => {
      render(
        <FormField
          label="Email"
          htmlFor="email"
          error="Invalid email"
          touched={false}
        >
          <input id="email" />
        </FormField>
      );
      expect(screen.queryByText("Invalid email")).not.toBeInTheDocument();
    });

    it("shows error when touched and has error", () => {
      render(
        <FormField
          label="Email"
          htmlFor="email"
          error="Invalid email"
          touched={true}
        >
          <input id="email" />
        </FormField>
      );
      expect(screen.getByText("Invalid email")).toBeInTheDocument();
    });

    it("error has alert role for accessibility", () => {
      render(
        <FormField
          label="Email"
          htmlFor="email"
          error="Invalid email"
          touched={true}
        >
          <input id="email" />
        </FormField>
      );
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });

    it("applies error styling to label when error is shown", () => {
      render(
        <FormField
          label="Email"
          htmlFor="email"
          error="Invalid email"
          touched={true}
        >
          <input id="email" />
        </FormField>
      );
      const label = screen.getByText("Email");
      expect(label).toHaveClass("text-red-600");
    });
  });

  describe("success state", () => {
    it("shows check icon when touched, valid, and no error", () => {
      render(
        <FormField label="Email" htmlFor="email" touched={true} valid={true}>
          <input id="email" />
        </FormField>
      );
      expect(screen.getByLabelText("Valid")).toBeInTheDocument();
    });

    it("does not show check icon when not touched", () => {
      render(
        <FormField label="Email" htmlFor="email" touched={false} valid={true}>
          <input id="email" />
        </FormField>
      );
      expect(screen.queryByLabelText("Valid")).not.toBeInTheDocument();
    });

    it("does not show check icon when there is an error", () => {
      render(
        <FormField
          label="Email"
          htmlFor="email"
          touched={true}
          valid={true}
          error="Invalid"
        >
          <input id="email" />
        </FormField>
      );
      expect(screen.queryByLabelText("Valid")).not.toBeInTheDocument();
    });
  });

  describe("character count", () => {
    it("displays character count when provided", () => {
      render(
        <FormField
          label="Name"
          htmlFor="name"
          characterCount={{ current: 10, max: 50 }}
        >
          <input id="name" />
        </FormField>
      );
      expect(screen.getByText("10/50")).toBeInTheDocument();
    });

    it("applies orange color when at max characters", () => {
      render(
        <FormField
          label="Name"
          htmlFor="name"
          characterCount={{ current: 50, max: 50 }}
        >
          <input id="name" />
        </FormField>
      );
      const countText = screen.getByText("50/50");
      expect(countText).toHaveClass("text-orange-600");
    });

    it("applies gray color when under max characters", () => {
      render(
        <FormField
          label="Name"
          htmlFor="name"
          characterCount={{ current: 25, max: 50 }}
        >
          <input id="name" />
        </FormField>
      );
      const countText = screen.getByText("25/50");
      expect(countText).toHaveClass("text-gray-500");
    });

    it("does not display character count when not provided", () => {
      render(
        <FormField label="Name" htmlFor="name">
          <input id="name" />
        </FormField>
      );
      expect(screen.queryByText(/\/\d+/)).not.toBeInTheDocument();
    });
  });

  describe("children rendering", () => {
    it("renders children content", () => {
      render(
        <FormField label="Email" htmlFor="email">
          <input id="email" data-testid="email-input" />
        </FormField>
      );
      expect(screen.getByTestId("email-input")).toBeInTheDocument();
    });

    it("renders multiple children", () => {
      render(
        <FormField label="Email" htmlFor="email">
          <input id="email" data-testid="email-input" />
          <span data-testid="helper-text">Enter your email</span>
        </FormField>
      );
      expect(screen.getByTestId("email-input")).toBeInTheDocument();
      expect(screen.getByTestId("helper-text")).toBeInTheDocument();
    });
  });

  describe("custom className", () => {
    it("applies custom className to container", () => {
      const { container } = render(
        <FormField label="Email" htmlFor="email" className="custom-class">
          <input id="email" />
        </FormField>
      );
      expect(container.firstChild).toHaveClass("custom-class");
    });
  });
});
