import { Progress } from '@/components/ui/progress';
import { Check } from 'lucide-react';
import { cn } from '@/lib/utils';
import {
  useCurrentStep,
  useProfileWizardStore,
  type WizardStep,
  TOTAL_STEPS,
} from '@/stores/profileWizard.store';

interface StepInfo {
  id: WizardStep;
  label: string;
  description: string;
}

const STEPS: StepInfo[] = [
  {
    id: 'personal-details',
    label: 'Personal Details',
    description: 'Phone, date of birth, preferences',
  },
  {
    id: 'address',
    label: 'Address',
    description: 'Add your shipping address',
  },
  {
    id: 'preferences',
    label: 'Preferences',
    description: 'Notification settings',
  },
  {
    id: 'review',
    label: 'Review',
    description: 'Confirm your information',
  },
];

export function WizardProgress() {
  const currentStep = useCurrentStep();
  const isStepComplete = useProfileWizardStore((state) => state.isStepComplete);
  const setCurrentStep = useProfileWizardStore((state) => state.setCurrentStep);

  const currentStepIndex = STEPS.findIndex((s) => s.id === currentStep);
  const progressPercentage = ((currentStepIndex + 1) / TOTAL_STEPS) * 100;

  return (
    <div className="mb-8">
      <div className="mb-4">
        <Progress value={progressPercentage} className="h-2" />
      </div>

      <nav aria-label="Progress">
        <ol role="list" className="flex items-center justify-between">
          {STEPS.map((step, index) => {
            const isComplete = isStepComplete(step.id);
            const isCurrent = step.id === currentStep;
            const isPast = index < currentStepIndex;
            const canNavigate = isPast || isComplete;

            return (
              <li key={step.id} className="flex-1">
                <button
                  type="button"
                  onClick={() => canNavigate && setCurrentStep(step.id)}
                  disabled={!canNavigate}
                  className={cn(
                    'group flex flex-col items-center w-full',
                    canNavigate && 'cursor-pointer',
                    !canNavigate && 'cursor-not-allowed'
                  )}
                >
                  <span
                    className={cn(
                      'flex h-10 w-10 items-center justify-center rounded-full border-2 text-sm font-medium',
                      isCurrent &&
                        'border-cyan-500 bg-cyan-500 text-white',
                      isComplete &&
                        !isCurrent &&
                        'border-green-500 bg-green-500 text-white',
                      !isCurrent &&
                        !isComplete &&
                        'border-gray-600 bg-gray-800 text-gray-400'
                    )}
                  >
                    {isComplete && !isCurrent ? (
                      <Check className="h-5 w-5" aria-hidden="true" />
                    ) : (
                      index + 1
                    )}
                  </span>
                  <span
                    className={cn(
                      'mt-2 text-xs font-medium text-center hidden sm:block',
                      isCurrent && 'text-cyan-400',
                      isComplete && !isCurrent && 'text-green-400',
                      !isCurrent && !isComplete && 'text-gray-500'
                    )}
                  >
                    {step.label}
                  </span>
                </button>
              </li>
            );
          })}
        </ol>
      </nav>

      <div className="mt-4 text-center">
        <h2 className="text-lg font-semibold text-white">
          {STEPS[currentStepIndex]?.label}
        </h2>
        <p className="text-sm text-gray-400">
          {STEPS[currentStepIndex]?.description}
        </p>
      </div>
    </div>
  );
}
