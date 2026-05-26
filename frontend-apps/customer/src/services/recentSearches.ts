const STORAGE_KEY_PREFIX = "acme-recent-searches";
const MAX_RECENT = 5;

function storageKey(customerId: string): string {
  return `${STORAGE_KEY_PREFIX}:${customerId}`;
}

export function getRecentSearches(customerId: string): string[] {
  try {
    const raw = localStorage.getItem(storageKey(customerId));
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function addRecentSearch(customerId: string, query: string): void {
  const trimmed = query.trim();
  if (!trimmed) return;

  const existing = getRecentSearches(customerId);
  const filtered = existing.filter(
    (s) => s.toLowerCase() !== trimmed.toLowerCase(),
  );
  const updated = [trimmed, ...filtered].slice(0, MAX_RECENT);

  try {
    localStorage.setItem(storageKey(customerId), JSON.stringify(updated));
  } catch {
    // localStorage full or unavailable — silently ignore
  }
}

export function clearRecentSearches(customerId: string): void {
  try {
    localStorage.removeItem(storageKey(customerId));
  } catch {
    // silently ignore
  }
}
