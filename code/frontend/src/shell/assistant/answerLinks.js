/**
 * The one way an AI answer becomes a link (W-114.13). The answer is text from a model, so nothing in it is
 * trusted: only the exact pattern `[label](app:/path)` is read, and it becomes a link only when the caller
 * may open that exact path - a screen of their own menu, or a fixed sub-route from `GET /api/v1/ai/link-targets`.
 * Anything else stays as the characters written: `https:`, `javascript:`, `app://host`, a path with an id,
 * a label with a line break, raw HTML.
 */

/** Label 1-80 characters with no bracket or line break; path of letters, digits, `/`, `-`, `_`. */
const LINK = /\[([^\]\r\n]{1,80})\]\(app:(\/[A-Za-z0-9/_-]*)\)/g;

/**
 * The answer as parts in order: `{ text }` and `{ label, path }`. A match whose path is not in
 * `allowedPaths` stays text, brackets and all; neighbouring text parts are joined.
 *
 * @param {string} text the answer
 * @param {Set<string>|string[]} allowedPaths the paths this caller may open
 */
export function splitAnswer(text, allowedPaths) {
  const source = String(text ?? '');
  const allowed = allowedPaths instanceof Set ? allowedPaths : new Set(allowedPaths || []);
  const parts = [];
  const pushText = (t) => {
    if (!t) return;
    const last = parts[parts.length - 1];
    if (last && last.text !== undefined) last.text += t;
    else parts.push({ text: t });
  };
  let at = 0;
  for (const match of source.matchAll(LINK)) {
    const [whole, label, path] = match;
    pushText(source.slice(at, match.index));
    // `app://evil` has no single leading slash path; `//` never names a screen.
    if (allowed.has(path) && !path.startsWith('//')) {
      parts.push({ label, path });
    } else {
      pushText(whole);
    }
    at = match.index + whole.length;
  }
  pushText(source.slice(at));
  return parts;
}

/** Every `path` in the navigation feed's items, children included. */
export function feedPaths(items) {
  const out = new Set();
  const walk = (list) => {
    (list || []).forEach((item) => {
      if (item && typeof item.path === 'string') out.add(item.path);
      walk(item?.children);
    });
  };
  walk(items);
  return out;
}
