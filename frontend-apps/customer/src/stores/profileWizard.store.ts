import { create } from 'zustand';
import type {
  PersonalDetailsFormData,
  AddressFormData,
  PreferencesFormData,
} from '@/schemas/profile.schema';

export type WizardStep = 'personal-details' | 'address' | 'preferences' | 'review';

interface ProfileWizardState {
  // Current step
  currentStep: WizardStep;

  // Step data
  personalDetails: PersonalDetailsFormData | null;
  address: AddressFormData | null;
  preferences: PreferencesFormData | null;

  // Completion tracking
  completedSteps: Set<WizardStep>;

  // Submission state
  isSubmitting: boolean;
  submitError: string | null;

  // Actions
  setCurrentStep: (step: WizardStep) => void;
  goToNextStep: () => void;
  goToPreviousStep: () => void;

  setPersonalDetails: (data: PersonalDetailsFormData) => void;
  setAddress: (data: AddressFormData | null) => void;
  setPreferences: (data: PreferencesFormData) => void;

  markStepComplete: (step: WizardStep) => void;
  isStepComplete: (step: WizardStep) => boolean;

  setSubmitting: (isSubmitting: boolean) => void;
  setSubmitError: (error: string | null) => void;

  reset: () => void;
  skipWizard: () => void;
}

const STEP_ORDER: WizardStep[] = ['personal-details', 'address', 'preferences', 'review'];

const getNextStep = (currentStep: WizardStep): WizardStep | null => {
  const currentIndex = STEP_ORDER.indexOf(currentStep);
  if (currentIndex < STEP_ORDER.length - 1) {
    return STEP_ORDER[currentIndex + 1];
  }
  return null;
};

const getPreviousStep = (currentStep: WizardStep): WizardStep | null => {
  const currentIndex = STEP_ORDER.indexOf(currentStep);
  if (currentIndex > 0) {
    return STEP_ORDER[currentIndex - 1];
  }
  return null;
};

const initialState = {
  currentStep: 'personal-details' as WizardStep,
  personalDetails: null,
  address: null,
  preferences: null,
  completedSteps: new Set<WizardStep>(),
  isSubmitting: false,
  submitError: null,
};

export const useProfileWizardStore = create<ProfileWizardState>((set, get) => ({
  ...initialState,

  setCurrentStep: (step) => set({ currentStep: step }),

  goToNextStep: () => {
    const { currentStep } = get();
    const nextStep = getNextStep(currentStep);
    if (nextStep) {
      set({ currentStep: nextStep });
    }
  },

  goToPreviousStep: () => {
    const { currentStep } = get();
    const previousStep = getPreviousStep(currentStep);
    if (previousStep) {
      set({ currentStep: previousStep });
    }
  },

  setPersonalDetails: (data) =>
    set({
      personalDetails: data,
      completedSteps: new Set([...get().completedSteps, 'personal-details']),
    }),

  setAddress: (data) =>
    set({
      address: data,
      completedSteps: data
        ? new Set([...get().completedSteps, 'address'])
        : get().completedSteps,
    }),

  setPreferences: (data) =>
    set({
      preferences: data,
      completedSteps: new Set([...get().completedSteps, 'preferences']),
    }),

  markStepComplete: (step) =>
    set({ completedSteps: new Set([...get().completedSteps, step]) }),

  isStepComplete: (step) => get().completedSteps.has(step),

  setSubmitting: (isSubmitting) => set({ isSubmitting }),

  setSubmitError: (error) => set({ submitError: error }),

  reset: () => set({ ...initialState, completedSteps: new Set() }),

  skipWizard: () => {
    // Navigate away from wizard - implementation depends on routing
    set({ ...initialState, completedSteps: new Set() });
  },
}));

// Selector hooks for common selections
export const useCurrentStep = () => useProfileWizardStore((state) => state.currentStep);
export const usePersonalDetails = () => useProfileWizardStore((state) => state.personalDetails);
export const useAddress = () => useProfileWizardStore((state) => state.address);
export const usePreferences = () => useProfileWizardStore((state) => state.preferences);
export const useIsSubmitting = () => useProfileWizardStore((state) => state.isSubmitting);

// Get progress percentage
export const useWizardProgress = () =>
  useProfileWizardStore((state) => {
    const stepIndex = STEP_ORDER.indexOf(state.currentStep);
    return ((stepIndex + 1) / STEP_ORDER.length) * 100;
  });

// Get current step index (1-based for display)
export const useCurrentStepNumber = () =>
  useProfileWizardStore((state) => {
    return STEP_ORDER.indexOf(state.currentStep) + 1;
  });

export const TOTAL_STEPS = STEP_ORDER.length;
