const publicIdPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

// QR contents are data, never an instruction to open an arbitrary URL.
export function publicTagId(input: string, origin: string): string | null {
  const value = input.trim();
  if (publicIdPattern.test(value)) return value.toLowerCase();
  try {
    const link = new URL(value);
    if (link.origin !== origin || link.search || link.hash) return null;
    const match = /^\/f\/([^/]+)\/?$/.exec(link.pathname);
    return match && publicIdPattern.test(match[1]) ? match[1].toLowerCase() : null;
  } catch { return null; }
}
