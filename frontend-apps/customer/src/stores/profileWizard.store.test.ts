import { describe, it, expect, beforeEach } from "vitest";
import { act, renderHook } from "@testing-library/react";
import {
  useProfileWizardStore,
  useCurrentStep,
  usePersonalDetails,
  useAddress,
  usePreferences,
  useIsSubmitting,
  useWizardProgress,
  useCurrentStepNumber,
  TOTAL_STEPS,
} from "./profileWizard.store";
import type {
  PersonalDetailsFormData,
  AddressFormData,
  PreferencesFormData,
} from "@/schemas/profile.schema";

describe("useProfileWizardStore", () => {
  beforeEach(() => {
    // Reset store to initial state before each test
    act(() => {
      useProfileWizardStore.getState().reset();
    });
  });

  describe("initial state", () => {
    it("starts on personal-details step", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      expect(result.current.currentStep).toBe("personal-details");
    });

    it("has null data for all steps initially", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      expect(result.current.personalDetails).toBeNull();
      expect(result.current.address).toBeNull();
      expect(result.current.preferences).toBeNull();
    });

    it("has empty completed steps set", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      expect(result.current.completedSteps.size).toBe(0);
    });

    it("is not submitting initially", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      expect(result.current.isSubmitting).toBe(false);
    });

    it("has no submit error initially", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      expect(result.current.submitError).toBeNull();
    });
  });

  describe("setCurrentStep", () => {
    it("changes the current step", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.setCurrentStep("address");
      });
      expect(result.current.currentStep).toBe("address");
    });

    it("can navigate to any step directly", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.setCurrentStep("review");
      });
      expect(result.current.currentStep).toBe("review");
    });
  });

  describe("goToNextStep", () => {
    it("navigates from personal-details to address", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.goToNextStep();
      });
      expect(result.current.currentStep).toBe("address");
    });

    it("navigates from address to preferences", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.setCurrentStep("address");
        result.current.goToNextStep();
      });
      expect(result.current.currentStep).toBe("preferences");
    });

    it("navigates from preferences to review", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.setCurrentStep("preferences");
        result.current.goToNextStep();
      });
      expect(result.current.currentStep).toBe("review");
    });

    it("stays on review when already on last step", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.setCurrentStep("review");
        result.current.goToNextStep();
      });
      expect(result.current.currentStep).toBe("review");
    });
  });

  describe("goToPreviousStep", () => {
    it("navigates from address to personal-details", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.setCurrentStep("address");
        result.current.goToPreviousStep();
      });
      expect(result.current.currentStep).toBe("personal-details");
    });

    it("navigates from preferences to address", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.setCurrentStep("preferences");
        result.current.goToPreviousStep();
      });
      expect(result.current.currentStep).toBe("address");
    });

    it("navigates from review to preferences", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.setCurrentStep("review");
        result.current.goToPreviousStep();
      });
      expect(result.current.currentStep).toBe("preferences");
    });

    it("stays on personal-details when already on first step", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.goToPreviousStep();
      });
      expect(result.current.currentStep).toBe("personal-details");
    });
  });

  describe("setPersonalDetails", () => {
    const personalDetails: PersonalDetailsFormData = {
      phoneCountryCode: "+1",
      phoneNumber: "5551234567",
      dateOfBirth: "1990-01-15",
      gender: "MALE",
      preferredLocale: "en-US",
      timezone: "America/New_York",
    };

    it("stores personal details data", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.setPersonalDetails(personalDetails);
      });
      expect(result.current.personalDetails).toEqual(personalDetails);
    });

    it("marks personal-details step as complete", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.setPersonalDetails(personalDetails);
      });
      expect(result.current.completedSteps.has("personal-details")).toBe(true);
    });
  });

  describe("setAddress", () => {
    const address: AddressFormData = {
      addressType: "SHIPPING",
      streetLine1: "123 Main St",
      city: "New York",
      stateProvince: "NY",
      postalCode: "10001",
      country: "US",
      isDefault: true,
    };

    it("stores address data", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.setAddress(address);
      });
      expect(result.current.address).toEqual(address);
    });

    it("marks address step as complete when data is provided", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.setAddress(address);
      });
      expect(result.current.completedSteps.has("address")).toBe(true);
    });

    it("does not mark address step complete when null is provided", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.setAddress(null);
      });
      expect(result.current.completedSteps.has("address")).toBe(false);
    });

    it("allows setting address to null", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.setAddress(address);
        result.current.setAddress(null);
      });
      expect(result.current.address).toBeNull();
    });
  });

  describe("setPreferences", () => {
    const preferences: PreferencesFormData = {
      emailNotifications: true,
      smsNotifications: false,
      pushNotifications: true,
      marketingCommunications: false,
      notificationFrequency: "DAILY_DIGEST",
    };

    it("stores preferences data", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.setPreferences(preferences);
      });
      expect(result.current.preferences).toEqual(preferences);
    });

    it("marks preferences step as complete", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.setPreferences(preferences);
      });
      expect(result.current.completedSteps.has("preferences")).toBe(true);
    });
  });

  describe("markStepComplete", () => {
    it("marks a step as complete", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.markStepComplete("address");
      });
      expect(result.current.completedSteps.has("address")).toBe(true);
    });

    it("can mark multiple steps as complete", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.markStepComplete("personal-details");
        result.current.markStepComplete("address");
        result.current.markStepComplete("preferences");
      });
      expect(result.current.completedSteps.size).toBe(3);
    });
  });

  describe("isStepComplete", () => {
    it("returns false for incomplete step", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      expect(result.current.isStepComplete("address")).toBe(false);
    });

    it("returns true for completed step", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.markStepComplete("address");
      });
      expect(result.current.isStepComplete("address")).toBe(true);
    });
  });

  describe("setSubmitting", () => {
    it("sets submitting state to true", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.setSubmitting(true);
      });
      expect(result.current.isSubmitting).toBe(true);
    });

    it("sets submitting state to false", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.setSubmitting(true);
        result.current.setSubmitting(false);
      });
      expect(result.current.isSubmitting).toBe(false);
    });
  });

  describe("setSubmitError", () => {
    it("sets submit error message", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.setSubmitError("Network error");
      });
      expect(result.current.submitError).toBe("Network error");
    });

    it("clears submit error when null is passed", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.setSubmitError("Some error");
        result.current.setSubmitError(null);
      });
      expect(result.current.submitError).toBeNull();
    });
  });

  describe("reset", () => {
    it("resets all state to initial values", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.setCurrentStep("preferences");
        result.current.setPersonalDetails({
          phoneNumber: "123",
        });
        result.current.markStepComplete("address");
        result.current.setSubmitting(true);
        result.current.setSubmitError("Error");
        result.current.reset();
      });
      expect(result.current.currentStep).toBe("personal-details");
      expect(result.current.personalDetails).toBeNull();
      expect(result.current.address).toBeNull();
      expect(result.current.preferences).toBeNull();
      expect(result.current.completedSteps.size).toBe(0);
      expect(result.current.isSubmitting).toBe(false);
      expect(result.current.submitError).toBeNull();
    });
  });

  describe("skipWizard", () => {
    it("resets state when skipping wizard", () => {
      const { result } = renderHook(() => useProfileWizardStore());
      act(() => {
        result.current.setCurrentStep("review");
        result.current.markStepComplete("personal-details");
        result.current.skipWizard();
      });
      expect(result.current.currentStep).toBe("personal-details");
      expect(result.current.completedSteps.size).toBe(0);
    });
  });
});

describe("selector hooks", () => {
  beforeEach(() => {
    act(() => {
      useProfileWizardStore.getState().reset();
    });
  });

  describe("useCurrentStep", () => {
    it("returns current step", () => {
      const { result } = renderHook(() => useCurrentStep());
      expect(result.current).toBe("personal-details");
    });
  });

  describe("usePersonalDetails", () => {
    it("returns personal details", () => {
      const { result } = renderHook(() => usePersonalDetails());
      expect(result.current).toBeNull();
    });
  });

  describe("useAddress", () => {
    it("returns address", () => {
      const { result } = renderHook(() => useAddress());
      expect(result.current).toBeNull();
    });
  });

  describe("usePreferences", () => {
    it("returns preferences", () => {
      const { result } = renderHook(() => usePreferences());
      expect(result.current).toBeNull();
    });
  });

  describe("useIsSubmitting", () => {
    it("returns submitting state", () => {
      const { result } = renderHook(() => useIsSubmitting());
      expect(result.current).toBe(false);
    });
  });

  describe("useWizardProgress", () => {
    it("returns 25% for first step", () => {
      const { result } = renderHook(() => useWizardProgress());
      expect(result.current).toBe(25);
    });

    it("returns 50% for second step", () => {
      act(() => {
        useProfileWizardStore.getState().setCurrentStep("address");
      });
      const { result } = renderHook(() => useWizardProgress());
      expect(result.current).toBe(50);
    });

    it("returns 75% for third step", () => {
      act(() => {
        useProfileWizardStore.getState().setCurrentStep("preferences");
      });
      const { result } = renderHook(() => useWizardProgress());
      expect(result.current).toBe(75);
    });

    it("returns 100% for last step", () => {
      act(() => {
        useProfileWizardStore.getState().setCurrentStep("review");
      });
      const { result } = renderHook(() => useWizardProgress());
      expect(result.current).toBe(100);
    });
  });

  describe("useCurrentStepNumber", () => {
    it("returns 1 for first step", () => {
      const { result } = renderHook(() => useCurrentStepNumber());
      expect(result.current).toBe(1);
    });

    it("returns 2 for second step", () => {
      act(() => {
        useProfileWizardStore.getState().setCurrentStep("address");
      });
      const { result } = renderHook(() => useCurrentStepNumber());
      expect(result.current).toBe(2);
    });

    it("returns 4 for last step", () => {
      act(() => {
        useProfileWizardStore.getState().setCurrentStep("review");
      });
      const { result } = renderHook(() => useCurrentStepNumber());
      expect(result.current).toBe(4);
    });
  });
});

describe("TOTAL_STEPS", () => {
  it("equals 4", () => {
    expect(TOTAL_STEPS).toBe(4);
  });
});
