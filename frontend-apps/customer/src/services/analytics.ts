/**
 * Analytics tracking seam.
 *
 * No real analytics provider is wired up yet. This module exists so
 * application code can emit named events without coupling to a specific
 * vendor. In development, events are logged to the console; in production,
 * they are a no-op until a provider is connected.
 *
 * TODO: integrate a real analytics provider (Mixpanel / Segment / etc.)
 * and replace the body of trackEvent here.
 */
export function trackEvent(
  name: string,
  properties?: Record<string, unknown>,
): void {
  if (import.meta.env.DEV) {
    // eslint-disable-next-line no-console
    console.debug("[analytics]", name, properties ?? {});
  }
}
