import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  preferencesSchema,
  type PreferencesFormData,
  NOTIFICATION_FREQUENCY_OPTIONS,
} from "@/schemas/profile.schema";
import {
  useProfileWizardStore,
  usePreferences,
} from "@/stores/profileWizard.store";

export function PreferencesStep() {
  const existingData = usePreferences();
  const setPreferences = useProfileWizardStore((state) => state.setPreferences);
  const goToNextStep = useProfileWizardStore((state) => state.goToNextStep);
  const goToPreviousStep = useProfileWizardStore(
    (state) => state.goToPreviousStep,
  );

  const { handleSubmit, control } = useForm<PreferencesFormData>({
    resolver: zodResolver(preferencesSchema),
    defaultValues: existingData || {
      emailNotifications: true,
      smsNotifications: false,
      pushNotifications: false,
      marketingCommunications: false,
      notificationFrequency: "IMMEDIATE",
    },
  });

  const onSubmit = (data: PreferencesFormData) => {
    setPreferences(data);
    goToNextStep();
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-8">
      {/* Communication Preferences */}
      <div className="space-y-6">
        <h3 className="text-lg font-medium text-white">
          Communication Preferences
        </h3>

        {/* Email Notifications */}
        <div className="flex items-center justify-between">
          <div className="space-y-0.5">
            <Label className="text-white">Email Notifications</Label>
            <p className="text-sm text-gray-400">
              Receive order updates and account alerts via email
            </p>
          </div>
          <Controller
            name="emailNotifications"
            control={control}
            render={({ field }) => (
              <Switch
                checked={field.value}
                onCheckedChange={field.onChange}
                aria-label="Email notifications"
              />
            )}
          />
        </div>

        {/* SMS Notifications */}
        <div className="flex items-center justify-between">
          <div className="space-y-0.5">
            <Label className="text-white">SMS Notifications</Label>
            <p className="text-sm text-gray-400">
              Receive text messages for delivery updates
            </p>
          </div>
          <Controller
            name="smsNotifications"
            control={control}
            render={({ field }) => (
              <Switch
                checked={field.value}
                onCheckedChange={field.onChange}
                aria-label="SMS notifications"
              />
            )}
          />
        </div>

        {/* Push Notifications */}
        <div className="flex items-center justify-between">
          <div className="space-y-0.5">
            <Label className="text-white">Push Notifications</Label>
            <p className="text-sm text-gray-400">
              Receive push notifications on your devices
            </p>
          </div>
          <Controller
            name="pushNotifications"
            control={control}
            render={({ field }) => (
              <Switch
                checked={field.value}
                onCheckedChange={field.onChange}
                aria-label="Push notifications"
              />
            )}
          />
        </div>

        {/* Marketing Communications */}
        <div className="flex items-center justify-between">
          <div className="space-y-0.5">
            <Label className="text-white">Marketing Communications</Label>
            <p className="text-sm text-gray-400">
              Receive promotional offers and product recommendations
            </p>
          </div>
          <Controller
            name="marketingCommunications"
            control={control}
            render={({ field }) => (
              <Switch
                checked={field.value}
                onCheckedChange={field.onChange}
                aria-label="Marketing communications"
              />
            )}
          />
        </div>
      </div>

      {/* Notification Frequency */}
      <div className="space-y-4">
        <h3 className="text-lg font-medium text-white">
          Notification Frequency
        </h3>
        <div className="space-y-2">
          <Label className="text-white">
            How often would you like to receive notifications?
          </Label>
          <Controller
            name="notificationFrequency"
            control={control}
            render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger className="w-full sm:w-64 bg-gray-800 border-gray-600 text-white">
                  <SelectValue placeholder="Select frequency" />
                </SelectTrigger>
                <SelectContent className="bg-gray-800 border-gray-600">
                  {NOTIFICATION_FREQUENCY_OPTIONS.map((option) => (
                    <SelectItem
                      key={option.value}
                      value={option.value}
                      className="text-white hover:bg-gray-700"
                    >
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        </div>
      </div>

      {/* Navigation Buttons */}
      <div className="flex justify-between pt-4">
        <Button
          type="button"
          variant="outline"
          onClick={goToPreviousStep}
          className="border-gray-600 text-gray-300 hover:bg-gray-700"
        >
          Back
        </Button>
        <Button
          type="submit"
          className="bg-cyan-600 hover:bg-cyan-700 text-white"
        >
          Review
        </Button>
      </div>
    </form>
  );
}
