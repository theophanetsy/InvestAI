/** Home page of a logged-in user. */
export const HOME_URL = '/dashboard';

/**
 * Where to go after logging in: the requested page when it is an internal path (`/…`, never
 * `//host`, and nothing a URL parser could turn into it), otherwise the home page. Meant for
 * `Router.navigateByUrl`, which stays in the application anyway; never for `window.location`.
 */
export function safeReturnUrl(value: string | undefined): string {
  if (!value?.startsWith('/') || value.startsWith('//') || hasAmbiguousCharacter(value)) {
    return HOME_URL;
  }
  return value;
}

const BACKSLASH = 0x5c;

/** Backslashes and control characters, which URL parsers drop or read as `/` (`/\t/evil`). */
function hasAmbiguousCharacter(value: string): boolean {
  for (let index = 0; index < value.length; index++) {
    const code = value.charCodeAt(index);
    if (code < 0x20 || code === 0x7f || code === BACKSLASH) {
      return true;
    }
  }
  return false;
}
