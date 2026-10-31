const THIS_SITE = "http://acme.invalid";

function staysOnThisSite(path: string): boolean {
  return new URL(path, THIS_SITE).origin === THIS_SITE;
}

/**
 * Whether a `redirect` value is a path on this site, so sign-in can't be used as an open redirect.
 * The URL parser decides, rather than a pattern match, since it is what catches `//host`, `/\host`
 * and the like the way a browser reads them. A path may hold `://`: only leaving the site matters.
 * The decoded form is checked too, for `/%2Fhost`. A value that won't decode isn't one of ours.
 */
export function isSafeRedirect(url: string): boolean {
  if (!url.startsWith("/")) return false;
  try {
    return staysOnThisSite(url) && staysOnThisSite(decodeURIComponent(url));
  } catch {
    return false;
  }
}
