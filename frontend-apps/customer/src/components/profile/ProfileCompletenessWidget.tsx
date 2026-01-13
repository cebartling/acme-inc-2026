import { useEffect, useState } from "react";
import { Link } from "@tanstack/react-router";
import {
  customerApi,
  ProfileCompletenessResponse,
  ProfileCompletenessSection,
} from "../../services/api";
import { cn } from "../../lib/utils";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "../ui/card";

/**
 * Props for the ProfileCompletenessWidget component.
 */
interface ProfileCompletenessWidgetProps {
  customerId: string;
  className?: string;
  compact?: boolean;
}

/**
 * Circular progress ring component for displaying the overall score.
 */
function CircularProgress({
  score,
  size = 120,
}: {
  score: number;
  size?: number;
}) {
  const strokeWidth = 8;
  const radius = (size - strokeWidth) / 2;
  const circumference = radius * 2 * Math.PI;
  const progress = (score / 100) * circumference;

  // Determine color based on score
  const getColor = () => {
    if (score >= 80) return "stroke-green-500";
    if (score >= 50) return "stroke-yellow-500";
    return "stroke-red-500";
  };

  return (
    <div className="relative inline-flex items-center justify-center">
      <svg
        width={size}
        height={size}
        className="-rotate-90 transform"
        aria-hidden="true"
      >
        {/* Background circle */}
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          className="stroke-gray-700"
          strokeWidth={strokeWidth}
          fill="none"
        />
        {/* Progress circle */}
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          className={cn("transition-all duration-500", getColor())}
          strokeWidth={strokeWidth}
          fill="none"
          strokeDasharray={circumference}
          strokeDashoffset={circumference - progress}
          strokeLinecap="round"
        />
      </svg>
      {/* Center text */}
      <div className="absolute flex flex-col items-center justify-center">
        <span className="text-2xl font-bold text-white">{score}%</span>
        <span className="text-xs text-gray-400">Complete</span>
      </div>
    </div>
  );
}

/**
 * Section breakdown item showing individual section progress.
 */
function SectionItem({
  section,
  expanded,
  onToggle,
}: {
  section: ProfileCompletenessSection;
  expanded: boolean;
  onToggle: () => void;
}) {
  return (
    <div className="border-b border-gray-700 last:border-b-0">
      <button
        type="button"
        onClick={onToggle}
        className="flex w-full items-center justify-between px-2 py-3 text-left transition-colors hover:bg-gray-700/50"
        aria-expanded={expanded}
      >
        <div className="flex items-center gap-3">
          {section.isComplete ? (
            <span className="flex h-5 w-5 items-center justify-center rounded-full bg-green-600">
              <svg
                className="h-3 w-3 text-white"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                aria-hidden="true"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={3}
                  d="M5 13l4 4L19 7"
                />
              </svg>
            </span>
          ) : (
            <span className="flex h-5 w-5 items-center justify-center rounded-full border-2 border-gray-500">
              <span className="sr-only">Incomplete</span>
            </span>
          )}
          <span className="text-sm font-medium text-white">
            {section.displayName}
          </span>
        </div>
        <div className="flex items-center gap-2">
          <span
            className={cn(
              "text-sm",
              section.isComplete ? "text-green-400" : "text-gray-400"
            )}
          >
            {section.score}%
          </span>
          <svg
            className={cn(
              "h-4 w-4 text-gray-400 transition-transform",
              expanded && "rotate-180"
            )}
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            aria-hidden="true"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M19 9l-7 7-7-7"
            />
          </svg>
        </div>
      </button>
      {expanded && (
        <div className="bg-gray-800/50 px-4 py-2">
          {section.items.map((item) => (
            <div
              key={item.name}
              className="flex items-center gap-2 py-1 text-sm"
            >
              {item.complete ? (
                <svg
                  className="h-4 w-4 text-green-400"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                  aria-hidden="true"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M5 13l4 4L19 7"
                  />
                </svg>
              ) : (
                <svg
                  className="h-4 w-4 text-gray-500"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                  aria-hidden="true"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M6 18L18 6M6 6l12 12"
                  />
                </svg>
              )}
              <span
                className={cn(
                  item.complete ? "text-gray-300" : "text-gray-400"
                )}
              >
                {formatItemName(item.name)}
                {item.action && !item.complete && (
                  <span className="ml-2 text-cyan-400">- {item.action}</span>
                )}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * Formats an item name from camelCase to a readable format.
 */
function formatItemName(name: string): string {
  return name
    .replace(/([A-Z])/g, " $1")
    .replace(/^./, (str) => str.toUpperCase())
    .trim();
}

/**
 * ProfileCompletenessWidget displays the user's profile completeness score
 * with a visual breakdown of completed and incomplete sections.
 *
 * Features:
 * - Circular progress ring showing overall percentage
 * - Expandable sections showing individual items
 * - Next recommended action with link
 * - Prompt to complete profile when below 80%
 */
export function ProfileCompletenessWidget({
  customerId,
  className,
  compact = false,
}: ProfileCompletenessWidgetProps) {
  const [data, setData] = useState<ProfileCompletenessResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [expandedSection, setExpandedSection] = useState<string | null>(null);

  useEffect(() => {
    async function fetchCompleteness() {
      try {
        setLoading(true);
        setError(null);
        const response = await customerApi.getProfileCompleteness(customerId);
        setData(response);
      } catch (err) {
        setError(
          err instanceof Error ? err.message : "Failed to load profile data"
        );
      } finally {
        setLoading(false);
      }
    }

    fetchCompleteness();
  }, [customerId]);

  if (loading) {
    return (
      <Card className={cn("animate-pulse bg-gray-800", className)}>
        <CardHeader>
          <div className="h-4 w-32 rounded bg-gray-700" />
        </CardHeader>
        <CardContent className="flex flex-col items-center gap-4">
          <div className="h-[120px] w-[120px] rounded-full bg-gray-700" />
          <div className="h-4 w-48 rounded bg-gray-700" />
        </CardContent>
      </Card>
    );
  }

  if (error) {
    return (
      <Card className={cn("bg-gray-800", className)}>
        <CardHeader>
          <CardTitle className="text-red-400">Error</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-gray-400">{error}</p>
        </CardContent>
      </Card>
    );
  }

  if (!data) {
    return null;
  }

  const needsAttention = data.overallScore < 80;

  if (compact) {
    return (
      <Card className={cn("bg-gray-800", className)}>
        <CardContent className="flex items-center justify-between p-4">
          <div className="flex items-center gap-4">
            <CircularProgress score={data.overallScore} size={60} />
            <div>
              <p className="font-medium text-white">Profile Completeness</p>
              {needsAttention && data.nextAction && (
                <p className="text-sm text-gray-400">
                  Next: {data.nextAction.action}
                </p>
              )}
            </div>
          </div>
          <Link
            to="/profile/complete"
            className="rounded-md bg-cyan-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-cyan-700"
          >
            Complete
          </Link>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className={cn("bg-gray-800", className)}>
      <CardHeader>
        <CardTitle className="text-white">Profile Completeness</CardTitle>
        <CardDescription>
          {data.overallScore >= 100
            ? "Your profile is complete!"
            : needsAttention
              ? "Complete your profile for a better experience"
              : "You're almost there! Just a few more steps."}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        {/* Progress Ring */}
        <div className="flex justify-center">
          <CircularProgress score={data.overallScore} />
        </div>

        {/* Next Action Banner */}
        {needsAttention && data.nextAction && (
          <div className="rounded-lg bg-cyan-900/30 p-4">
            <p className="mb-2 text-sm font-medium text-cyan-300">
              Recommended Next Step
            </p>
            <div className="flex items-center justify-between">
              <p className="text-sm text-gray-300">{data.nextAction.action}</p>
              <Link
                to={data.nextAction.url}
                className="rounded-md bg-cyan-600 px-3 py-1.5 text-sm font-medium text-white transition-colors hover:bg-cyan-700"
              >
                Complete Now
              </Link>
            </div>
          </div>
        )}

        {/* Section Breakdown */}
        <div className="rounded-lg border border-gray-700">
          {data.sections.map((section) => (
            <SectionItem
              key={section.name}
              section={section}
              expanded={expandedSection === section.name}
              onToggle={() =>
                setExpandedSection(
                  expandedSection === section.name ? null : section.name
                )
              }
            />
          ))}
        </div>

        {/* Complete Profile Button */}
        {data.overallScore < 100 && (
          <div className="flex justify-center">
            <Link
              to="/profile/complete"
              className="rounded-md bg-cyan-600 px-6 py-2 font-medium text-white transition-colors hover:bg-cyan-700"
            >
              Complete Your Profile
            </Link>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
