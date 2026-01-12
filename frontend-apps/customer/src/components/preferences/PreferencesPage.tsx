import { useState } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  fullPreferencesSchema,
  FullPreferencesData,
  NOTIFICATION_FREQUENCY_OPTIONS,
  CURRENCY_OPTIONS,
  LANGUAGE_OPTIONS,
  TIMEZONE_OPTIONS,
} from "@/schemas/profile.schema";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
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
  Bell,
  Mail,
  MessageSquare,
  Smartphone,
  Shield,
  Globe,
  CheckCircle2,
  AlertCircle,
} from "lucide-react";
import { customerApi, ApiError } from "@/services/api";
import { useUserId } from "@/stores/auth.store";

interface PreferencesPageProps {
  customerId: string;
  initialPreferences?: FullPreferencesData;
  onSave?: (data: FullPreferencesData) => Promise<void>;
}

export function PreferencesPage({
  customerId,
  initialPreferences,
  onSave,
}: PreferencesPageProps) {
  const userId = useUserId();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitStatus, setSubmitStatus] = useState<
    "idle" | "success" | "error"
  >("idle");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const defaultValues: FullPreferencesData = initialPreferences || {
    communication: {
      email: true,
      sms: false,
      push: false,
      marketing: false,
      frequency: "IMMEDIATE",
    },
    privacy: {
      shareDataWithPartners: false,
      allowAnalytics: true,
      allowPersonalization: true,
    },
    display: {
      language: "en-US",
      currency: "USD",
      timezone: "UTC",
    },
  };

  const {
    control,
    handleSubmit,
    formState: { isDirty },
  } = useForm<FullPreferencesData>({
    resolver: zodResolver(fullPreferencesSchema),
    defaultValues,
  });

  const onSubmit = async (data: FullPreferencesData) => {
    setIsSubmitting(true);
    setSubmitStatus("idle");
    setErrorMessage(null);

    try {
      if (onSave) {
        // Use provided onSave callback (for testing)
        await onSave(data);
      } else if (userId) {
        // Call Customer Service API
        await customerApi.updatePreferences(customerId, userId, {
          communication: {
            email: data.communication.email,
            sms: data.communication.sms,
            push: data.communication.push,
            marketing: data.communication.marketing,
            frequency: data.communication.frequency,
          },
          privacy: {
            shareDataWithPartners: data.privacy.shareDataWithPartners,
            allowAnalytics: data.privacy.allowAnalytics,
            allowPersonalization: data.privacy.allowPersonalization,
          },
          display: {
            language: data.display.language,
            currency: data.display.currency,
            timezone: data.display.timezone,
          },
        });
      } else {
        throw new Error("User not authenticated");
      }
      setSubmitStatus("success");
      setTimeout(() => setSubmitStatus("idle"), 3000);
    } catch (error) {
      setSubmitStatus("error");
      if (error instanceof ApiError) {
        // Handle specific API errors
        if (error.data?.error === "PHONE_NOT_VERIFIED") {
          setErrorMessage(
            "Please verify your phone number to enable SMS notifications"
          );
        } else if (error.data?.error === "UNSUPPORTED_LANGUAGE") {
          setErrorMessage("The selected language is not supported");
        } else {
          setErrorMessage(error.message);
        }
      } else {
        setErrorMessage(
          error instanceof Error ? error.message : "Failed to save preferences"
        );
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="container mx-auto max-w-3xl py-8 px-4">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900 dark:text-white">
          Preferences
        </h1>
        <p className="mt-2 text-gray-600 dark:text-gray-400">
          Manage your communication, privacy, and display settings.
        </p>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        {/* Communication Preferences */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Bell className="h-5 w-5" />
              Communication Preferences
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-6">
            {/* Email Notifications */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <Mail className="h-5 w-5 text-gray-500" />
                <div>
                  <Label htmlFor="email-notifications" className="font-medium">
                    Email Notifications
                  </Label>
                  <p className="text-sm text-gray-500">
                    Receive transactional emails about your orders and account
                  </p>
                </div>
              </div>
              <Controller
                name="communication.email"
                control={control}
                render={({ field }) => (
                  <Switch
                    id="email-notifications"
                    checked={field.value}
                    onCheckedChange={field.onChange}
                  />
                )}
              />
            </div>

            {/* SMS Notifications */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <MessageSquare className="h-5 w-5 text-gray-500" />
                <div>
                  <Label htmlFor="sms-notifications" className="font-medium">
                    SMS Notifications
                  </Label>
                  <p className="text-sm text-gray-500">
                    Receive text messages for important updates
                  </p>
                </div>
              </div>
              <Controller
                name="communication.sms"
                control={control}
                render={({ field }) => (
                  <Switch
                    id="sms-notifications"
                    checked={field.value}
                    onCheckedChange={field.onChange}
                  />
                )}
              />
            </div>

            {/* Push Notifications */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <Smartphone className="h-5 w-5 text-gray-500" />
                <div>
                  <Label htmlFor="push-notifications" className="font-medium">
                    Push Notifications
                  </Label>
                  <p className="text-sm text-gray-500">
                    Receive push notifications on your devices
                  </p>
                </div>
              </div>
              <Controller
                name="communication.push"
                control={control}
                render={({ field }) => (
                  <Switch
                    id="push-notifications"
                    checked={field.value}
                    onCheckedChange={field.onChange}
                  />
                )}
              />
            </div>

            {/* Marketing Communications */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <Mail className="h-5 w-5 text-gray-500" />
                <div>
                  <Label
                    htmlFor="marketing-communications"
                    className="font-medium"
                  >
                    Marketing Communications
                  </Label>
                  <p className="text-sm text-gray-500">
                    Receive promotional content, offers, and newsletters
                  </p>
                </div>
              </div>
              <Controller
                name="communication.marketing"
                control={control}
                render={({ field }) => (
                  <Switch
                    id="marketing-communications"
                    checked={field.value}
                    onCheckedChange={field.onChange}
                  />
                )}
              />
            </div>

            {/* Notification Frequency */}
            <div className="space-y-2">
              <Label htmlFor="notification-frequency" className="font-medium">
                Notification Frequency
              </Label>
              <Controller
                name="communication.frequency"
                control={control}
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="notification-frequency">
                      <SelectValue placeholder="Select frequency" />
                    </SelectTrigger>
                    <SelectContent>
                      {NOTIFICATION_FREQUENCY_OPTIONS.map((option) => (
                        <SelectItem key={option.value} value={option.value}>
                          {option.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
          </CardContent>
        </Card>

        {/* Privacy Preferences */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Shield className="h-5 w-5" />
              Privacy Preferences
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-6">
            {/* Share Data with Partners */}
            <div className="flex items-center justify-between">
              <div>
                <Label
                  htmlFor="share-data-partners"
                  className="font-medium block"
                >
                  Share Data with Partners
                </Label>
                <p className="text-sm text-gray-500">
                  Allow third-party data sharing for enhanced services
                </p>
              </div>
              <Controller
                name="privacy.shareDataWithPartners"
                control={control}
                render={({ field }) => (
                  <Switch
                    id="share-data-partners"
                    checked={field.value}
                    onCheckedChange={field.onChange}
                  />
                )}
              />
            </div>

            {/* Allow Analytics */}
            <div className="flex items-center justify-between">
              <div>
                <Label htmlFor="allow-analytics" className="font-medium block">
                  Allow Analytics
                </Label>
                <p className="text-sm text-gray-500">
                  Help us improve by allowing usage analytics
                </p>
              </div>
              <Controller
                name="privacy.allowAnalytics"
                control={control}
                render={({ field }) => (
                  <Switch
                    id="allow-analytics"
                    checked={field.value}
                    onCheckedChange={field.onChange}
                  />
                )}
              />
            </div>

            {/* Allow Personalization */}
            <div className="flex items-center justify-between">
              <div>
                <Label
                  htmlFor="allow-personalization"
                  className="font-medium block"
                >
                  Allow Personalization
                </Label>
                <p className="text-sm text-gray-500">
                  Enable personalized recommendations and content
                </p>
              </div>
              <Controller
                name="privacy.allowPersonalization"
                control={control}
                render={({ field }) => (
                  <Switch
                    id="allow-personalization"
                    checked={field.value}
                    onCheckedChange={field.onChange}
                  />
                )}
              />
            </div>
          </CardContent>
        </Card>

        {/* Display Preferences */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Globe className="h-5 w-5" />
              Display Preferences
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-6">
            {/* Language */}
            <div className="space-y-2">
              <Label htmlFor="language" className="font-medium">
                Language
              </Label>
              <Controller
                name="display.language"
                control={control}
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="language">
                      <SelectValue placeholder="Select language" />
                    </SelectTrigger>
                    <SelectContent>
                      {LANGUAGE_OPTIONS.map((option) => (
                        <SelectItem key={option.value} value={option.value}>
                          {option.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>

            {/* Currency */}
            <div className="space-y-2">
              <Label htmlFor="currency" className="font-medium">
                Currency
              </Label>
              <Controller
                name="display.currency"
                control={control}
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="currency">
                      <SelectValue placeholder="Select currency" />
                    </SelectTrigger>
                    <SelectContent>
                      {CURRENCY_OPTIONS.map((option) => (
                        <SelectItem key={option.value} value={option.value}>
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
              <Label htmlFor="timezone" className="font-medium">
                Timezone
              </Label>
              <Controller
                name="display.timezone"
                control={control}
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="timezone">
                      <SelectValue placeholder="Select timezone" />
                    </SelectTrigger>
                    <SelectContent>
                      {TIMEZONE_OPTIONS.map((option) => (
                        <SelectItem key={option.value} value={option.value}>
                          {option.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
          </CardContent>
        </Card>

        {/* Submit Button and Status */}
        <div className="flex items-center justify-between">
          <div>
            {submitStatus === "success" && (
              <div className="flex items-center gap-2 text-green-600">
                <CheckCircle2 className="h-5 w-5" />
                <span>Preferences saved successfully</span>
              </div>
            )}
            {submitStatus === "error" && (
              <div className="flex items-center gap-2 text-red-600">
                <AlertCircle className="h-5 w-5" />
                <span>{errorMessage || "Failed to save preferences"}</span>
              </div>
            )}
          </div>
          <Button type="submit" disabled={isSubmitting || !isDirty}>
            {isSubmitting ? "Saving..." : "Save Preferences"}
          </Button>
        </div>
      </form>
    </div>
  );
}
