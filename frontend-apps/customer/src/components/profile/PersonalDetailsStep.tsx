import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { FormField } from '@/components/registration/FormField';
import {
  personalDetailsSchema,
  type PersonalDetailsFormData,
  COUNTRY_CODES,
  GENDER_OPTIONS,
  LOCALE_OPTIONS,
  TIMEZONE_OPTIONS,
} from '@/schemas/profile.schema';
import { useProfileWizardStore, usePersonalDetails } from '@/stores/profileWizard.store';

export function PersonalDetailsStep() {
  const existingData = usePersonalDetails();
  const setPersonalDetails = useProfileWizardStore((state) => state.setPersonalDetails);
  const goToNextStep = useProfileWizardStore((state) => state.goToNextStep);

  const {
    register,
    handleSubmit,
    control,
    formState: { errors, touchedFields, dirtyFields },
  } = useForm<PersonalDetailsFormData>({
    resolver: zodResolver(personalDetailsSchema),
    mode: 'onBlur',
    defaultValues: existingData || {
      phoneCountryCode: '+1',
      phoneNumber: '',
      dateOfBirth: '',
      gender: undefined,
      preferredLocale: 'en-US',
      timezone: 'UTC',
    },
  });

  const onSubmit = (data: PersonalDetailsFormData) => {
    setPersonalDetails(data);
    goToNextStep();
  };

  const handleSkip = () => {
    // Save empty data and proceed
    setPersonalDetails({});
    goToNextStep();
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
      {/* Phone Number */}
      <div className="space-y-2">
        <Label className="text-white">Phone Number (optional)</Label>
        <div className="flex gap-2">
          <Controller
            name="phoneCountryCode"
            control={control}
            render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger className="w-32 bg-gray-800 border-gray-600 text-white">
                  <SelectValue placeholder="Code" />
                </SelectTrigger>
                <SelectContent className="bg-gray-800 border-gray-600">
                  {COUNTRY_CODES.map((code) => (
                    <SelectItem
                      key={code.code}
                      value={code.code}
                      className="text-white hover:bg-gray-700"
                    >
                      {code.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
          <Input
            id="phoneNumber"
            type="tel"
            placeholder="555-123-4567"
            className="flex-1 bg-gray-800 border-gray-600 text-white placeholder:text-gray-500"
            {...register('phoneNumber')}
          />
        </div>
        {errors.phoneNumber && (
          <p className="text-sm text-red-500" role="alert">
            {errors.phoneNumber.message}
          </p>
        )}
      </div>

      {/* Date of Birth */}
      <FormField
        label="Date of Birth (optional)"
        htmlFor="dateOfBirth"
        error={errors.dateOfBirth?.message}
        touched={touchedFields.dateOfBirth}
        valid={dirtyFields.dateOfBirth && !errors.dateOfBirth}
      >
        <Input
          id="dateOfBirth"
          type="date"
          className="bg-gray-800 border-gray-600 text-white"
          {...register('dateOfBirth')}
        />
      </FormField>

      {/* Gender */}
      <div className="space-y-2">
        <Label className="text-white">Gender (optional)</Label>
        <Controller
          name="gender"
          control={control}
          render={({ field }) => (
            <Select value={field.value} onValueChange={field.onChange}>
              <SelectTrigger className="bg-gray-800 border-gray-600 text-white">
                <SelectValue placeholder="Select gender" />
              </SelectTrigger>
              <SelectContent className="bg-gray-800 border-gray-600">
                {GENDER_OPTIONS.map((option) => (
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

      {/* Preferred Language */}
      <div className="space-y-2">
        <Label className="text-white">Preferred Language (optional)</Label>
        <Controller
          name="preferredLocale"
          control={control}
          render={({ field }) => (
            <Select value={field.value} onValueChange={field.onChange}>
              <SelectTrigger className="bg-gray-800 border-gray-600 text-white">
                <SelectValue placeholder="Select language" />
              </SelectTrigger>
              <SelectContent className="bg-gray-800 border-gray-600">
                {LOCALE_OPTIONS.map((option) => (
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

      {/* Timezone */}
      <div className="space-y-2">
        <Label className="text-white">Timezone (optional)</Label>
        <Controller
          name="timezone"
          control={control}
          render={({ field }) => (
            <Select value={field.value} onValueChange={field.onChange}>
              <SelectTrigger className="bg-gray-800 border-gray-600 text-white">
                <SelectValue placeholder="Select timezone" />
              </SelectTrigger>
              <SelectContent className="bg-gray-800 border-gray-600">
                {TIMEZONE_OPTIONS.map((option) => (
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

      {/* Navigation Buttons */}
      <div className="flex justify-between pt-4">
        <Button
          type="button"
          variant="outline"
          onClick={handleSkip}
          className="border-gray-600 text-gray-300 hover:bg-gray-700"
        >
          Skip This Step
        </Button>
        <Button
          type="submit"
          className="bg-cyan-600 hover:bg-cyan-700 text-white"
        >
          Continue
        </Button>
      </div>
    </form>
  );
}
