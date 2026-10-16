/** Only allow same-origin relative paths as post-login redirect targets. */
export function safeNext(raw: string | null, fallback = '/tickets'): string {
  if (!raw) return fallback;
  if (!raw.startsWith('/') || raw.startsWith('//') || raw.startsWith('/\\')) return fallback;
  if (raw.startsWith('/login') || raw.startsWith('/register')) return fallback;
  return raw;
}
