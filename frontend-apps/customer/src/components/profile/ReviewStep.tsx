import { Loader2, Edit2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  useProfileWizardStore,
  usePersonalDetails,
  useAddress,
  usePreferences,
  useIsSubmitting,
} from "@/stores/profileWizard.store";
import {
  GENDER_OPTIONS,
  LOCALE_OPTIONS,
  TIMEZONE_OPTIONS,
  ADDRESS_TYPE_OPTIONS,
  US_STATES,
  COUNTRY_OPTIONS,
  NOTIFICATION_FREQUENCY_OPTIONS,
} from "@/schemas/profile.schema";

interface ReviewSectionProps {
  title: string;
  onEdit: () => void;
  children: React.ReactNode;
}

function ReviewSection({ title, onEdit, children }: ReviewSectionProps) {
  return (
    <Card className="bg-gray-800 border-gray-700">
      <CardHeader className="flex flex-row items-center justify-between py-3">
        <CardTitle className="text-lg text-white">{title}</CardTitle>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={onEdit}
          className="text-cyan-400 hover:text-cyan-300 hover:bg-gray-700"
        >
          <Edit2 className="h-4 w-4 mr-1" />
          Edit
        </Button>
      </CardHeader>
      <CardContent className="py-3">{children}</CardContent>
    </Card>
  );
}

interface ReviewItemProps {
  label: string;
  value: string | undefined | null;
}

function ReviewItem({ label, value }: ReviewItemProps) {
  return (
    <div className="flex justify-between py-1">
      <span className="text-gray-400">{label}</span>
      <span className="text-white">{value || "Not provided"}</span>
    </div>
  );
}

function getLabelFromOptions(
  value: string | undefined,
  options: { value: string; label: string }[],
): string | undefined {
  if (!value) return undefined;
  return options.find((o) => o.value === value)?.label;
}

interface ReviewStepProps {
  onSubmit: () => Promise<void>;
}

export function ReviewStep({ onSubmit }: ReviewStepProps) {
  const personalDetails = usePersonalDetails();
  const address = useAddress();
  const preferences = usePreferences();
  const isSubmitting = useIsSubmitting();
  const setCurrentStep = useProfileWizardStore((state) => state.setCurrentStep);
  const goToPreviousStep = useProfileWizardStore(
    (state) => state.goToPreviousStep,
  );
  const setSubmitting = useProfileWizardStore((state) => state.setSubmitting);
  const setSubmitError = useProfileWizardStore((state) => state.setSubmitError);
  const submitError = useProfileWizardStore((state) => state.submitError);

  const handleSubmit = async () => {
    setSubmitting(true);
    setSubmitError(null);
    try {
      await onSubmit();
    } catch (error) {
      setSubmitError(
        error instanceof Error
          ? error.message
          : "An error occurred while saving your profile",
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <p className="text-gray-400 text-center">
        Please review your information before submitting.
      </p>

      {/* Personal Details Section */}
      <ReviewSection
        title="Personal Details"
        onEdit={() => setCurrentStep("personal-details")}
      >
        <div className="space-y-1">
          {personalDetails?.phoneNumber && (
            <ReviewItem
              label="Phone"
              value={`${personalDetails.phoneCountryCode} ${personalDetails.phoneNumber}`}
            />
          )}
          <ReviewItem
            label="Date of Birth"
            value={personalDetails?.dateOfBirth}
          />
          <ReviewItem
            label="Gender"
            value={getLabelFromOptions(personalDetails?.gender, GENDER_OPTIONS)}
          />
          <ReviewItem
            label="Language"
            value={getLabelFromOptions(
              personalDetails?.preferredLocale,
              LOCALE_OPTIONS,
            )}
          />
          <ReviewItem
            label="Timezone"
            value={getLabelFromOptions(
              personalDetails?.timezone,
              TIMEZONE_OPTIONS,
            )}
          />
        </div>
      </ReviewSection>

      {/* Address Section */}
      <ReviewSection title="Address" onEdit={() => setCurrentStep("address")}>
        {address ? (
          <div className="space-y-1">
            <ReviewItem
              label="Type"
              value={getLabelFromOptions(
                address.addressType,
                ADDRESS_TYPE_OPTIONS,
              )}
            />
            {address.label && (
              <ReviewItem label="Label" value={address.label} />
            )}
            <ReviewItem label="Street" value={address.streetLine1} />
            {address.streetLine2 && (
              <ReviewItem label="" value={address.streetLine2} />
            )}
            <ReviewItem
              label="City"
              value={`${address.city}, ${getLabelFromOptions(address.stateProvince, US_STATES) || address.stateProvince} ${address.postalCode}`}
            />
            <ReviewItem
              label="Country"
              value={getLabelFromOptions(address.country, COUNTRY_OPTIONS)}
            />
            <ReviewItem
              label="Default"
              value={address.isDefault ? "Yes" : "No"}
            />
          </div>
        ) : (
          <p className="text-gray-500 italic">No address added</p>
        )}
      </ReviewSection>

      {/* Preferences Section */}
      <ReviewSection
        title="Communication Preferences"
        onEdit={() => setCurrentStep("preferences")}
      >
        <div className="space-y-1">
          <ReviewItem
            label="Email Notifications"
            value={preferences?.emailNotifications ? "Enabled" : "Disabled"}
          />
          <ReviewItem
            label="SMS Notifications"
            value={preferences?.smsNotifications ? "Enabled" : "Disabled"}
          />
          <ReviewItem
            label="Push Notifications"
            value={preferences?.pushNotifications ? "Enabled" : "Disabled"}
          />
          <ReviewItem
            label="Marketing"
            value={
              preferences?.marketingCommunications ? "Enabled" : "Disabled"
            }
          />
          <ReviewItem
            label="Frequency"
            value={getLabelFromOptions(
              preferences?.notificationFrequency,
              NOTIFICATION_FREQUENCY_OPTIONS,
            )}
          />
        </div>
      </ReviewSection>

      {/* Error Message */}
      {submitError && (
        <div className="bg-red-900/50 border border-red-500 rounded-md p-4">
          <p className="text-red-300">{submitError}</p>
        </div>
      )}

      {/* Navigation Buttons */}
      <div className="flex justify-between pt-4">
        <Button
          type="button"
          variant="outline"
          onClick={goToPreviousStep}
          disabled={isSubmitting}
          className="border-gray-600 text-gray-300 hover:bg-gray-700"
        >
          Back
        </Button>
        <Button
          type="button"
          onClick={handleSubmit}
          disabled={isSubmitting}
          className="bg-green-600 hover:bg-green-700 text-white"
        >
          {isSubmitting ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              Saving...
            </>
          ) : (
            "Complete Profile"
          )}
        </Button>
      </div>
    </div>
  );
}
