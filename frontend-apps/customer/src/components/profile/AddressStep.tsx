import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { FormField } from "@/components/registration/FormField";
import {
  addressSchema,
  type AddressFormData,
  ADDRESS_TYPE_OPTIONS,
  COUNTRY_OPTIONS,
  US_STATES,
} from "@/schemas/profile.schema";
import {
  useProfileWizardStore,
  useAddress,
} from "@/stores/profileWizard.store";

export function AddressStep() {
  const existingData = useAddress();
  const setAddress = useProfileWizardStore((state) => state.setAddress);
  const goToNextStep = useProfileWizardStore((state) => state.goToNextStep);
  const goToPreviousStep = useProfileWizardStore(
    (state) => state.goToPreviousStep,
  );

  const {
    register,
    handleSubmit,
    control,
    watch,
    formState: { errors, touchedFields, dirtyFields, isSubmitted },
  } = useForm<AddressFormData>({
    resolver: zodResolver(addressSchema),
    mode: "onBlur",
    defaultValues: existingData || {
      addressType: "BOTH",
      label: "",
      streetLine1: "",
      streetLine2: "",
      city: "",
      stateProvince: "",
      postalCode: "",
      country: "US",
      isDefault: true,
    },
  });

  const selectedCountry = watch("country");

  const onSubmit = (data: AddressFormData) => {
    setAddress(data);
    goToNextStep();
  };

  const handleSkip = () => {
    // Skip address step - save null
    setAddress(null);
    goToNextStep();
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
      {/* Address Type */}
      <div className="space-y-2">
        <Label className="text-white">
          Address Type <span className="text-red-500">*</span>
        </Label>
        <Controller
          name="addressType"
          control={control}
          render={({ field }) => (
            <Select value={field.value} onValueChange={field.onChange}>
              <SelectTrigger className="bg-gray-800 border-gray-600 text-white">
                <SelectValue placeholder="Select address type" />
              </SelectTrigger>
              <SelectContent className="bg-gray-800 border-gray-600">
                {ADDRESS_TYPE_OPTIONS.map((option) => (
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
        {(touchedFields.addressType || isSubmitted) && errors.addressType && (
          <p className="text-sm text-red-500" role="alert">
            {errors.addressType.message}
          </p>
        )}
      </div>

      {/* Label */}
      <FormField
        label="Address Label (optional)"
        htmlFor="label"
        error={errors.label?.message}
        touched={touchedFields.label}
        valid={dirtyFields.label && !errors.label}
      >
        <Input
          id="label"
          type="text"
          placeholder="e.g., Home, Office"
          className="bg-gray-800 border-gray-600 text-white placeholder:text-gray-500"
          {...register("label")}
        />
      </FormField>

      {/* Street Address Line 1 */}
      <FormField
        label="Street Address"
        htmlFor="streetLine1"
        required
        error={errors.streetLine1?.message}
        touched={touchedFields.streetLine1 || isSubmitted}
        valid={dirtyFields.streetLine1 && !errors.streetLine1}
      >
        <Input
          id="streetLine1"
          type="text"
          placeholder="123 Main St"
          className="bg-gray-800 border-gray-600 text-white placeholder:text-gray-500"
          {...register("streetLine1")}
        />
      </FormField>

      {/* Street Address Line 2 */}
      <FormField
        label="Apt, Suite, etc. (optional)"
        htmlFor="streetLine2"
        error={errors.streetLine2?.message}
        touched={touchedFields.streetLine2}
        valid={dirtyFields.streetLine2 && !errors.streetLine2}
      >
        <Input
          id="streetLine2"
          type="text"
          placeholder="Apt 4B"
          className="bg-gray-800 border-gray-600 text-white placeholder:text-gray-500"
          {...register("streetLine2")}
        />
      </FormField>

      {/* City and State Row */}
      <div className="grid grid-cols-2 gap-4">
        <FormField
          label="City"
          htmlFor="city"
          required
          error={errors.city?.message}
          touched={touchedFields.city || isSubmitted}
          valid={dirtyFields.city && !errors.city}
        >
          <Input
            id="city"
            type="text"
            placeholder="City"
            className="bg-gray-800 border-gray-600 text-white placeholder:text-gray-500"
            {...register("city")}
          />
        </FormField>

        <div className="space-y-2">
          <Label className="text-white">
            State/Province <span className="text-red-500">*</span>
          </Label>
          {selectedCountry === "US" ? (
            <Controller
              name="stateProvince"
              control={control}
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger className="bg-gray-800 border-gray-600 text-white">
                    <SelectValue placeholder="Select state" />
                  </SelectTrigger>
                  <SelectContent className="bg-gray-800 border-gray-600 max-h-60">
                    {US_STATES.map((state) => (
                      <SelectItem
                        key={state.value}
                        value={state.value}
                        className="text-white hover:bg-gray-700"
                      >
                        {state.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          ) : (
            <Input
              id="stateProvince"
              type="text"
              placeholder="State/Province"
              className="bg-gray-800 border-gray-600 text-white placeholder:text-gray-500"
              {...register("stateProvince")}
            />
          )}
          {(touchedFields.stateProvince || isSubmitted) &&
            errors.stateProvince && (
              <p className="text-sm text-red-500" role="alert">
                {errors.stateProvince.message}
              </p>
            )}
        </div>
      </div>

      {/* Postal Code and Country Row */}
      <div className="grid grid-cols-2 gap-4">
        <FormField
          label="Postal Code"
          htmlFor="postalCode"
          required
          error={errors.postalCode?.message}
          touched={touchedFields.postalCode || isSubmitted}
          valid={dirtyFields.postalCode && !errors.postalCode}
        >
          <Input
            id="postalCode"
            type="text"
            placeholder="12345"
            className="bg-gray-800 border-gray-600 text-white placeholder:text-gray-500"
            {...register("postalCode")}
          />
        </FormField>

        <div className="space-y-2">
          <Label className="text-white">
            Country <span className="text-red-500">*</span>
          </Label>
          <Controller
            name="country"
            control={control}
            render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger className="bg-gray-800 border-gray-600 text-white">
                  <SelectValue placeholder="Select country" />
                </SelectTrigger>
                <SelectContent className="bg-gray-800 border-gray-600">
                  {COUNTRY_OPTIONS.map((country) => (
                    <SelectItem
                      key={country.value}
                      value={country.value}
                      className="text-white hover:bg-gray-700"
                    >
                      {country.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
          {(touchedFields.country || isSubmitted) && errors.country && (
            <p className="text-sm text-red-500" role="alert">
              {errors.country.message}
            </p>
          )}
        </div>
      </div>

      {/* Default Address Checkbox */}
      <div className="flex items-center space-x-2">
        <Controller
          name="isDefault"
          control={control}
          render={({ field }) => (
            <Checkbox
              id="isDefault"
              checked={field.value}
              onCheckedChange={field.onChange}
              className="border-gray-600"
            />
          )}
        />
        <Label htmlFor="isDefault" className="text-gray-300">
          Set as default address
        </Label>
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
        <div className="space-x-2">
          <Button
            type="button"
            variant="outline"
            onClick={handleSkip}
            className="border-gray-600 text-gray-300 hover:bg-gray-700"
          >
            Skip
          </Button>
          <Button
            type="submit"
            className="bg-cyan-600 hover:bg-cyan-700 text-white"
          >
            Continue
          </Button>
        </div>
      </div>
    </form>
  );
}
