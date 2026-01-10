import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { WizardProgress } from "./WizardProgress";
import { PersonalDetailsStep } from "./PersonalDetailsStep";
import { AddressStep } from "./AddressStep";
import { PreferencesStep } from "./PreferencesStep";
import { ReviewStep } from "./ReviewStep";
import {
  useCurrentStep,
  useProfileWizardStore,
} from "@/stores/profileWizard.store";
import type { ProfileUpdateData } from "@/schemas/profile.schema";

interface ProfileWizardProps {
  customerId: string;
  onComplete: () => void;
  onSkip: () => void;
}

export function ProfileWizard({
  customerId,
  onComplete,
  onSkip,
}: ProfileWizardProps) {
  const currentStep = useCurrentStep();
  const personalDetails = useProfileWizardStore(
    (state) => state.personalDetails,
  );
  const address = useProfileWizardStore((state) => state.address);
  const preferences = useProfileWizardStore((state) => state.preferences);
  const reset = useProfileWizardStore((state) => state.reset);

  const handleSubmit = async () => {
    // Build the profile update payload
    const profileUpdate: ProfileUpdateData = {};

    if (personalDetails?.phoneNumber && personalDetails?.phoneCountryCode) {
      profileUpdate.phone = {
        countryCode: personalDetails.phoneCountryCode,
        number: personalDetails.phoneNumber,
      };
    }

    if (personalDetails?.dateOfBirth) {
      profileUpdate.dateOfBirth = personalDetails.dateOfBirth;
    }

    if (personalDetails?.gender) {
      profileUpdate.gender = personalDetails.gender;
    }

    if (personalDetails?.preferredLocale) {
      profileUpdate.preferredLocale = personalDetails.preferredLocale;
    }

    if (personalDetails?.timezone) {
      profileUpdate.timezone = personalDetails.timezone;
    }

    // TODO: Call the API to update the profile
    // For now, we'll simulate an API call
    console.log("Submitting profile update:", {
      customerId,
      profile: profileUpdate,
      address,
      preferences,
    });

    // Simulate API call
    await new Promise((resolve) => setTimeout(resolve, 1500));

    // Reset the wizard and call onComplete
    reset();
    onComplete();
  };

  const handleSkipAll = () => {
    reset();
    onSkip();
  };

  const renderStep = () => {
    switch (currentStep) {
      case "personal-details":
        return <PersonalDetailsStep />;
      case "address":
        return <AddressStep />;
      case "preferences":
        return <PreferencesStep />;
      case "review":
        return <ReviewStep onSubmit={handleSubmit} />;
      default:
        return <PersonalDetailsStep />;
    }
  };

  return (
    <Card className="w-full max-w-2xl mx-auto bg-gray-900 border-gray-700">
      <CardHeader className="text-center">
        <CardTitle className="text-2xl text-white">
          Complete Your Profile
        </CardTitle>
        <CardDescription className="text-gray-400">
          Add more details to personalize your shopping experience
        </CardDescription>
      </CardHeader>
      <CardContent>
        <WizardProgress />
        {renderStep()}

        {/* Skip All Button - only show on first step */}
        {currentStep === "personal-details" && (
          <div className="mt-6 text-center">
            <Button
              variant="link"
              onClick={handleSkipAll}
              className="text-gray-500 hover:text-gray-400"
            >
              Skip profile completion for now
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

export { WizardProgress } from "./WizardProgress";
export { PersonalDetailsStep } from "./PersonalDetailsStep";
export { AddressStep } from "./AddressStep";
export { PreferencesStep } from "./PreferencesStep";
export { ReviewStep } from "./ReviewStep";
