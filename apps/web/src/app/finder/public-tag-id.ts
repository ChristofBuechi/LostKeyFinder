import { isUuidV4 } from '../core/uuid';
// QR contents are data, never an instruction to open an arbitrary URL.
export function publicTagId(input: string, origin: string): string | null {
  const value = input.trim();
  if (isUuidV4(value)) return value.toLowerCase();
  try {
    const link = new URL(value);
    if (link.origin !== origin || link.search || link.hash) return null;
    const match = /^\/f\/([^/]+)\/?$/.exec(link.pathname);
    return match && isUuidV4(match[1]) ? match[1].toLowerCase() : null;
  } catch { return null; }
}
